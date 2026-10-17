package com.ezygo.attendance.ui.samarth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.remote.SamarthError
import com.ezygo.attendance.data.repo.SamarthRepository
import com.ezygo.attendance.domain.Cgpa
import com.ezygo.attendance.data.repo.StoredGradeCard
import com.ezygo.attendance.domain.model.SamarthProfile
import com.ezygo.attendance.domain.model.SemesterResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** The message to show for a failure, without leaking anything the portal said verbatim. */
private fun Throwable.samarthMessage(): String = when (this) {
    is SamarthError -> message ?: "Something went wrong with the Samarth portal."
    else -> "Something went wrong reaching the Samarth portal."
}

// ---- grade cards ----------------------------------------------------------

data class GradeCardsUiState(
    val cards: List<StoredGradeCard> = emptyList(),
    val results: List<SemesterResult> = emptyList(),
    val hasCredentials: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
    /** Set when the portal will not accept what is stored, or nothing is stored yet. */
    val askForCredentials: Boolean = false,
    val credentialsError: String? = null,
    /** Set briefly after a card is written to the device's Downloads folder. */
    val savedTo: String? = null,
) {
    /**
     * Whether every semester read carries a credit total.
     *
     * The weighted formula needs all of them: leaving out the one semester whose card
     * did not print its credits would drop that semester from the CGPA entirely, which
     * is a worse answer than an unweighted one.
     */
    val isCreditWeighted: Boolean get() = Cgpa.isCreditWeighted(results)

    /**
     * CGPA = Σ(Cᵢ × Sᵢ) / ΣCᵢ, over the semesters actually read.
     *
     * The arithmetic lives in [Cgpa] because the SGPA estimator projects against this
     * very figure, and the two had grown separate copies of it.
     */
    val cgpa: Double? get() = Cgpa.of(results)

    /** Total credits behind the CGPA, when it is the weighted one. */
    val totalCredits: Int? get() = if (isCreditWeighted) results.sumOf { it.credits ?: 0 } else null
}

@HiltViewModel
class GradeCardsViewModel @Inject constructor(
    private val repository: SamarthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GradeCardsUiState())
    val state: StateFlow<GradeCardsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.hasCredentials.collect { has ->
                _state.update { it.copy(hasCredentials = has) }
            }
        }
        viewModelScope.launch {
            repository.semesterResults.collect { results ->
                _state.update { it.copy(results = results) }
            }
        }
        // Whatever is already on disk, shown before anything touches the network.
        loadStored()
    }

    private fun loadStored() = viewModelScope.launch {
        _state.update { it.copy(cards = repository.storedGradeCards()) }
        // Cards downloaded before the reader existed still have to yield their SGPA,
        // so anything already on disk is read once on open. Re-reading a card that was
        // already parsed simply rewrites the same row.
        runCatching { repository.readStoredResults() }
    }

    /** Writes a copy into the device's Downloads folder. */
    fun saveToDevice(term: String) = viewModelScope.launch {
        val file = fileForTerm(term) ?: return@launch
        val name = repository.saveToDownloads(file)
        _state.update {
            if (name == null) {
                it.copy(error = "Could not save the grade card to Downloads.")
            } else {
                it.copy(savedTo = name)
            }
        }
    }

    fun dismissSaved() = _state.update { it.copy(savedTo = null) }

    /** The user asking for their grade cards, which is what signs in to the portal. */
    fun fetch(force: Boolean = false) {
        if (!_state.value.hasCredentials) {
            _state.update { it.copy(askForCredentials = true, credentialsError = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            try {
                val cards = repository.fetchGradeCards(force = force)
                _state.update { it.copy(isBusy = false, cards = cards) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _state.update {
                    if (throwable is SamarthError.BadCredentials) {
                        // The saved sign-in stopped working: ask again rather than
                        // leaving the user with an error and no way to fix it.
                        it.copy(
                            isBusy = false,
                            askForCredentials = true,
                            credentialsError = throwable.samarthMessage(),
                        )
                    } else {
                        it.copy(isBusy = false, error = throwable.samarthMessage())
                    }
                }
            }
        }
    }

    /**
     * Saves the sign-in and immediately uses it.
     *
     * Both halves are fetched, not just the one that asked: the credentials exist to
     * make the portal automatic, and making the user press a second button on the other
     * screen to get their profile would defeat the point of storing them at all.
     */
    fun saveCredentials(username: String, password: String) = viewModelScope.launch {
        repository.saveCredentials(username, password)
        _state.update { it.copy(askForCredentials = false, credentialsError = null) }
        runCatching { repository.refreshProfile() }
        fetch()
    }

    fun dismissCredentials() = _state.update { it.copy(askForCredentials = false) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun fileForTerm(term: String): File? =
        _state.value.cards.firstOrNull { it.card.term == term }?.file
}

// ---- the detailed profile -------------------------------------------------

data class SamarthProfileUiState(
    val hasCredentials: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val askForCredentials: Boolean = false,
    val credentialsError: String? = null,
)

/**
 * The cached Samarth profile.
 *
 * Nothing here fetches on open. The screen shows what was stored the last time the
 * user pressed Refresh, and only [refresh] ever changes it — which is what makes the
 * date beside it mean something.
 */
@HiltViewModel
class SamarthProfileViewModel @Inject constructor(
    private val repository: SamarthRepository,
) : ViewModel() {

    val profile: StateFlow<SamarthProfile?> = repository.profile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _state = MutableStateFlow(SamarthProfileUiState())
    val state: StateFlow<SamarthProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.hasCredentials.collect { has ->
                _state.update { it.copy(hasCredentials = has) }
            }
        }
    }

    fun refresh() {
        if (!_state.value.hasCredentials) {
            _state.update { it.copy(askForCredentials = true, credentialsError = null) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, error = null) }
            try {
                repository.refreshProfile()
                _state.update { it.copy(isRefreshing = false) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _state.update {
                    if (throwable is SamarthError.BadCredentials) {
                        it.copy(
                            isRefreshing = false,
                            askForCredentials = true,
                            credentialsError = throwable.samarthMessage(),
                        )
                    } else {
                        it.copy(isRefreshing = false, error = throwable.samarthMessage())
                    }
                }
            }
        }
    }

    /** Saves the sign-in and fetches both halves at once — see the grade card model. */
    fun saveCredentials(username: String, password: String) = viewModelScope.launch {
        repository.saveCredentials(username, password)
        _state.update { it.copy(askForCredentials = false, credentialsError = null) }
        refresh()
        runCatching { repository.fetchGradeCards() }
    }

    fun dismissCredentials() = _state.update { it.copy(askForCredentials = false) }

    fun dismissError() = _state.update { it.copy(error = null) }
}
