package com.ezygo.attendance.data.remote

import com.ezygo.attendance.di.SamarthHttpClient
import com.ezygo.attendance.domain.model.SamarthGradeCard
import com.ezygo.attendance.domain.model.SamarthProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** What can go wrong on the portal, in terms a screen can put in front of someone. */
sealed class SamarthError(message: String) : Exception(message) {
    data object BadCredentials : SamarthError("That Samarth username or password was not accepted.")
    data object Unreachable : SamarthError("Could not reach the Samarth portal. Check your connection.")
    data class Unexpected(val detail: String) : SamarthError(detail)
}

/**
 * The Samarth portal, read by scraping the pages a browser would be shown.
 *
 * There is no API here — only Yii-rendered HTML — so every step below mirrors what a
 * person clicking through the site does: pick up the CSRF token from the login page,
 * post the form, then follow the same links they would.
 *
 * A session is opened per operation rather than kept alive. The portal hands out a
 * PHPSESSID with no documented lifetime and no refresh, so holding one would mean
 * guessing when it went stale and recovering from being wrong; signing in again costs
 * two requests and cannot be wrong.
 */
@Singleton
class SamarthClient @Inject constructor(
    @SamarthHttpClient private val baseClient: OkHttpClient,
) {

    /**
     * Signs in and runs [block] against the resulting session.
     *
     * The cookie jar lives exactly as long as this call, so nothing carries between
     * operations and nothing about the portal is left in a shared client.
     */
    suspend fun <T> withSession(
        username: String,
        password: String,
        block: suspend (SamarthSession) -> T,
    ): T = withContext(Dispatchers.IO) {
        val session = SamarthSession(baseClient.newBuilder().cookieJar(MemoryCookieJar()).build())
        session.login(username, password)
        block(session)
    }

    companion object {
        const val BASE_URL = "https://cusat.samarth.edu.in"
    }
}

/** One signed-in visit to the portal. */
class SamarthSession internal constructor(private val client: OkHttpClient) {

    internal suspend fun login(username: String, password: String) {
        // The login form carries a CSRF token in a meta tag; posting without it is
        // rejected before the credentials are even looked at.
        val loginPage = document("/index.php/site/login")
        val csrf = loginPage.selectFirst("meta[name=csrf-token]")?.attr("content").orEmpty()
        if (csrf.isBlank()) throw SamarthError.Unexpected("The portal's login page did not look as expected.")

        val form = FormBody.Builder()
            .add("_csrf", csrf)
            .add("LoginForm[username]", username)
            .add("LoginForm[password]", password)
            .add("login-button", "")
            .build()

        val body = post("/index.php/site/login", form)

        // Presence of a PHPSESSID proves nothing: the portal issues one to anonymous
        // visitors on the first page load, so the original script's check passed even
        // for a wrong password. What actually separates the two cases is whether the
        // login form comes back.
        if (body.contains("LoginForm[password]") || body.contains("LoginForm%5Bpassword%5D")) {
            throw SamarthError.BadCredentials
        }
    }

    /** The student's own record, plus the programme page it links to. */
    internal suspend fun profile(nowEpochMs: Long): SamarthProfile {
        val page = document("/index.php/vidhyarthi/profile/index")
        if (page.valueFor("Full Name") == null) throw SamarthError.BadCredentials

        // The programme details sit behind a "Click here" link rather than on the
        // profile page itself; a profile without one is still worth showing.
        val programme = page.select("a")
            .firstOrNull { it.text().contains("Click here", ignoreCase = true) }
            ?.href()
            ?.let { runCatching { document(it) }.getOrNull() }

        return SamarthProfile(
            fullName = page.valueFor("Full Name").orNotAvailable(),
            dateOfBirth = page.valueFor("Date of Birth").orNotAvailable(),
            email = page.valueFor("Applicant's Email").orNotAvailable(),
            phone = page.valueFor("Mobile Number").orNotAvailable(),
            category = page.valueFor("Category").orNotAvailable(),
            addressLine = page.valueFor("Address Line 1").orNotAvailable(),
            district = page.valueFor("District").orNotAvailable(),
            statePincode = page.valueFor("State, Pincode").orNotAvailable(),
            enrolmentNumber = programme?.valueFor("Enrolment Number").orNotAvailable(),
            abcId = programme?.valueFor("ABC ID").orNotAvailable(),
            department = programme?.valueFor("Organizational unit").orNotAvailable(),
            fetchedEpochMs = nowEpochMs,
        )
    }

    /** Every grade card the portal is currently offering. */
    internal suspend fun gradeCards(): List<SamarthGradeCard> {
        val dashboard = document("/index.php/dashboard")
        val link = dashboard.select("a")
            .firstOrNull { it.text().contains("Grade Card/Result", ignoreCase = true) }
            ?.href()
            ?: throw SamarthError.Unexpected("The portal's dashboard is not offering grade cards.")

        return document(link).select("a#mark").mapIndexedNotNull { index, button ->
            val url = button.href()?.let { absolute(it) } ?: return@mapIndexedNotNull null
            SamarthGradeCard(
                // The portal numbers its own terms; the position is only a fallback
                // for a link that omits it, so two cards can never collide.
                term = url.queryParameter("term") ?: (index + 1).toString(),
                programmeId = url.queryParameter("programme_id").orEmpty(),
                examSessionId = url.queryParameter("exam_session_id").orEmpty(),
                studentId = url.queryParameter("student_id").orEmpty(),
            )
        }
    }

    /** The card itself, as the PDF bytes the portal serves. */
    internal suspend fun gradeCardPdf(card: SamarthGradeCard): ByteArray {
        val path = "/index.php/examstudent/grade-card/view" +
            "?programme_id=${card.programmeId}" +
            "&term=${card.term}" +
            "&exam_session_id=${card.examSessionId}" +
            "&student_id=${card.studentId}"
        val bytes = bytes(path)
        // An expired session answers with the login page rather than an error, and a
        // saved HTML file named .pdf is a worse failure than none at all.
        if (!bytes.looksLikePdf()) throw SamarthError.Unexpected("The portal returned a page instead of a grade card.")
        return bytes
    }

    // ---- plumbing --------------------------------------------------------

    private suspend fun document(pathOrUrl: String): Document =
        Jsoup.parse(get(pathOrUrl), SamarthClient.BASE_URL)

    private suspend fun get(pathOrUrl: String): String = withContext(Dispatchers.IO) {
        val url = absolute(pathOrUrl) ?: throw SamarthError.Unexpected("The portal gave a link that could not be followed.")
        call(Request.Builder().url(url).build()) { it.body.string() }
    }

    private suspend fun bytes(pathOrUrl: String): ByteArray = withContext(Dispatchers.IO) {
        val url = absolute(pathOrUrl) ?: throw SamarthError.Unexpected("The portal gave a link that could not be followed.")
        call(Request.Builder().url(url).build()) { it.body.bytes() }
    }

    private suspend fun post(path: String, form: FormBody): String = withContext(Dispatchers.IO) {
        val url = absolute(path) ?: throw SamarthError.Unexpected("The portal's login address could not be built.")
        call(Request.Builder().url(url).post(form).build()) { it.body.string() }
    }

    private fun <T> call(request: Request, read: (Response) -> T): T = try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw SamarthError.Unexpected("The portal answered with ${response.code}.")
            }
            read(response)
        }
    } catch (error: SamarthError) {
        throw error
    } catch (io: IOException) {
        throw SamarthError.Unreachable
    }

    private fun absolute(pathOrUrl: String): HttpUrl? =
        if (pathOrUrl.startsWith("http")) {
            pathOrUrl.toHttpUrlOrNull()
        } else {
            (SamarthClient.BASE_URL + pathOrUrl).toHttpUrlOrNull()
        }
}

// ---- parsing -------------------------------------------------------------

/**
 * The cell beside the header whose text starts with [label].
 *
 * The portal lays its record out as `<th>Label</th><td>value</td>` throughout, and the
 * labels carry trailing colons and stray whitespace inconsistently — so the match is on
 * the prefix rather than the whole string, exactly as the original script's regexes did.
 */
private fun Document.valueFor(label: String): String? = select("th")
    .firstOrNull { it.text().trim().startsWith(label, ignoreCase = true) }
    ?.nextElementSibling()
    ?.takeIf { it.tagName().equals("td", ignoreCase = true) }
    ?.text()
    ?.trim()
    ?.takeIf { it.isNotBlank() }

private fun String?.orNotAvailable(): String = this ?: SamarthProfile.NOT_AVAILABLE

/**
 * A link's target, resolved against the page it was found on.
 *
 * `absUrl` rather than `attr`, so a link written relative to the current directory
 * rather than to the site root still points somewhere.
 */
private fun Element.href(): String? =
    absUrl("href").takeIf { it.isNotBlank() } ?: attr("href").takeIf { it.isNotBlank() }

/** `%PDF` — enough to tell a document from the login page served in its place. */
private fun ByteArray.looksLikePdf(): Boolean =
    size > 4 && this[0] == 0x25.toByte() && this[1] == 0x50.toByte() &&
        this[2] == 0x44.toByte() && this[3] == 0x46.toByte()

/**
 * Cookies for exactly one visit.
 *
 * Not persisted anywhere: a PHPSESSID is a live credential, and the session it belongs
 * to is discarded as soon as the operation that opened it finishes.
 */
private class MemoryCookieJar : CookieJar {
    private val jar = mutableMapOf<String, Cookie>()

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        jar.values.filter { it.matches(url) }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { jar[it.name] = it }
    }
}
