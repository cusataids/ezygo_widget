package com.ezygo.attendance.data.repo

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.ezygo.attendance.data.local.AppDatabase
import com.ezygo.attendance.data.local.SamarthCredentialStore
import com.ezygo.attendance.data.local.entity.SamarthProfileEntity
import com.ezygo.attendance.data.local.entity.SemesterCourseEntity
import com.ezygo.attendance.data.local.entity.SemesterResultEntity
import com.ezygo.attendance.data.pdf.GradeCardReader
import com.ezygo.attendance.data.remote.SamarthClient
import com.ezygo.attendance.data.remote.SamarthError
import com.ezygo.attendance.domain.model.SamarthGradeCard
import com.ezygo.attendance.domain.model.SamarthProfile
import com.ezygo.attendance.domain.model.SemesterCourse
import com.ezygo.attendance.domain.model.SemesterCourses
import com.ezygo.attendance.domain.model.SemesterResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A grade card as it exists on this device. */
data class StoredGradeCard(
    val card: SamarthGradeCard,
    val file: File,
    val downloadedEpochMs: Long,
)

/**
 * Everything the app knows about the Samarth portal.
 *
 * Two different caching rules live here, deliberately:
 *
 * The **profile** is written only when the user asks. Nothing refreshes it on open, on
 * launch, or alongside an EzyGo refresh — the date it carries is the date they last
 * pressed Refresh, and that is the whole point of caching it rather than fetching it.
 *
 * **Grade cards** are fetched on request, because that is what asking for one means.
 * The PDFs stay on disk afterwards so an already-downloaded card opens without the
 * network, and without asking the portal for the same document twice.
 */
@Singleton
class SamarthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val credentials: SamarthCredentialStore,
    private val client: SamarthClient,
    private val gradeCardReader: GradeCardReader,
) {

    /** Whether the user has signed in to the portal at least once. */
    val hasCredentials: Flow<Boolean> = credentials.hasCredentials

    val profile: Flow<SamarthProfile?> =
        database.samarthDao().profile().map { it?.toDomain() }

    /** Each semester's result, read off the downloaded cards, earliest first. */
    val semesterResults: Flow<List<SemesterResult>> =
        database.semesterResultDao().results().map { rows ->
            rows.map { SemesterResult(it.semester, it.sgpa, it.credits) }
        }

    /** The individual courses behind those results, grouped by semester. */
    val semesterCourses: Flow<List<SemesterCourses>> =
        database.semesterCourseDao().all().map { rows ->
            rows.groupBy { it.semester }
                .toSortedMap()
                .map { (semester, courses) ->
                    SemesterCourses(
                        semester = semester,
                        courses = courses.map {
                            SemesterCourse(it.semester, it.code, it.name, it.credits, it.grade)
                        },
                    )
                }
        }

    suspend fun saveCredentials(username: String, password: String) =
        credentials.save(username, password)

    /**
     * Re-reads the profile from the portal and replaces the cached copy.
     *
     * The only writer of that cache. Throws [SamarthError.BadCredentials] when the
     * saved sign-in no longer works, which is the caller's cue to ask again.
     */
    suspend fun refreshProfile(): SamarthProfile {
        val saved = credentials.current() ?: throw SamarthError.BadCredentials
        val fetched = client.withSession(saved.username, saved.password) { session ->
            session.profile(System.currentTimeMillis())
        }
        database.samarthDao().upsert(fetched.toEntity())
        return fetched
    }

    /** Grade cards already downloaded, readable with no network at all. */
    suspend fun storedGradeCards(): List<StoredGradeCard> = withContext(Dispatchers.IO) {
        gradeCardDir().listFiles().orEmpty()
            .filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
            .mapNotNull { file ->
                val term = TERM_PATTERN.find(file.name)?.groupValues?.getOrNull(1)
                    ?: return@mapNotNull null
                StoredGradeCard(
                    // The ids are only needed to fetch a card; one already on disk is
                    // opened by its file, so they are left empty rather than invented.
                    card = SamarthGradeCard(term = term, programmeId = "", examSessionId = "", studentId = ""),
                    file = file,
                    downloadedEpochMs = file.lastModified(),
                )
            }
            .sortedByDescending { it.card.term.toIntOrNull() ?: 0 }
    }

    /**
     * Signs in, asks the portal what it is offering, and downloads anything missing.
     *
     * [force] re-downloads cards already held, for a term the portal has revised.
     */
    suspend fun fetchGradeCards(force: Boolean = false): List<StoredGradeCard> {
        val saved = credentials.current() ?: throw SamarthError.BadCredentials
        val downloaded = client.withSession(saved.username, saved.password) { session ->
            val cards = session.gradeCards()
            if (cards.isEmpty()) throw SamarthError.Unexpected("The portal is not offering any grade cards yet.")

            cards.mapNotNull { card ->
                val file = File(gradeCardDir(), card.fileName)
                if (file.exists() && !force) return@mapNotNull StoredGradeCard(card, file, file.lastModified())
                // One card failing must not cost the rest of the fetch: a revised
                // term can be missing from the portal while its neighbours are fine.
                runCatching {
                    val bytes = session.gradeCardPdf(card)
                    withContext(Dispatchers.IO) {
                        gradeCardDir().mkdirs()
                        file.writeBytes(bytes)
                    }
                    StoredGradeCard(card, file, System.currentTimeMillis())
                }.onFailure {
                    Log.w(TAG, "Could not download the grade card for term ${card.term}", it)
                }.getOrNull()
            }
        }
        if (downloaded.isEmpty()) throw SamarthError.Unexpected("None of the grade cards could be downloaded.")
        // Read straight after downloading, so the CGPA is right the moment a new card
        // lands rather than at the next time something happens to look.
        readResults(downloaded)
        return downloaded.sortedByDescending { it.card.term.toIntOrNull() ?: 0 }
    }

    /**
     * Extracts each card's semester and SGPA and stores them.
     *
     * A card that cannot be read is skipped in silence: the point of the CGPA is that
     * it is an average of results actually printed on cards, so a card that yields
     * nothing readable must contribute nothing rather than a guess. Storing by semester
     * is what stops a re-download counting the same term twice.
     */
    suspend fun readResults(cards: List<StoredGradeCard>) {
        for (stored in cards) {
            val reading = gradeCardReader.read(
                file = stored.file,
                fallbackSemester = stored.card.term.toIntOrNull(),
            ) ?: continue
            val result = reading.result
            database.semesterResultDao().upsert(
                SemesterResultEntity(
                    semester = result.semester,
                    sgpa = result.sgpa,
                    credits = result.credits,
                    parsedEpochMs = System.currentTimeMillis(),
                ),
            )
            // Only replaced when something was actually read. A layout this reader
            // cannot follow must not wipe a breakdown an earlier read did manage.
            if (reading.courses.isNotEmpty()) {
                database.semesterCourseDao().deleteSemester(result.semester)
                database.semesterCourseDao().upsert(
                    reading.courses.map {
                        SemesterCourseEntity(
                            semester = it.semester,
                            code = it.code,
                            name = it.name,
                            credits = it.credits,
                            grade = it.grade,
                        )
                    },
                )
            }
        }
    }

    /** Re-reads whatever is already on disk, for cards downloaded before this existed. */
    suspend fun readStoredResults() = readResults(storedGradeCards())

    /**
     * Copies a card into the device's Downloads folder.
     *
     * Through MediaStore rather than by writing to external storage directly, so it
     * needs no permission on any supported version and lands somewhere the user's file
     * manager can actually see. Returns the name it was saved under, or null on failure.
     */
    suspend fun saveToDownloads(file: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val name = file.name
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching null
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            name
        }.onFailure {
            Log.w(TAG, "Could not save a grade card to Downloads", it)
        }.getOrNull()
    }

    /**
     * Forgets everything about the portal: the sign-in, the cached profile, the PDFs.
     *
     * Called both by its own button and by signing out, because a grade card is as
     * much the user's private data as the attendance it sits beside.
     */
    suspend fun clear() {
        credentials.clear()
        database.samarthDao().clear()
        database.semesterResultDao().clearAll()
        database.semesterCourseDao().clearAll()
        withContext(Dispatchers.IO) {
            runCatching { gradeCardDir().deleteRecursively() }
                .onFailure { Log.w(TAG, "Could not delete the stored grade cards", it) }
        }
    }

    private fun gradeCardDir(): File = File(context.filesDir, "samarth/grade_cards")

    private fun SamarthProfileEntity.toDomain() = SamarthProfile(
        fullName = fullName,
        dateOfBirth = dateOfBirth,
        email = email,
        phone = phone,
        category = category,
        addressLine = addressLine,
        district = district,
        statePincode = statePincode,
        enrolmentNumber = enrolmentNumber,
        abcId = abcId,
        department = department,
        fetchedEpochMs = fetchedEpochMs,
    )

    private fun SamarthProfile.toEntity() = SamarthProfileEntity(
        fullName = fullName,
        dateOfBirth = dateOfBirth,
        email = email,
        phone = phone,
        category = category,
        addressLine = addressLine,
        district = district,
        statePincode = statePincode,
        enrolmentNumber = enrolmentNumber,
        abcId = abcId,
        department = department,
        fetchedEpochMs = fetchedEpochMs,
    )

    private companion object {
        const val TAG = "SamarthRepository"

        /** Matches the name [SamarthGradeCard.fileName] writes. */
        val TERM_PATTERN = Regex("""grade_card_term_(.+)\.pdf""", RegexOption.IGNORE_CASE)
    }
}
