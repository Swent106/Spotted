// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.vote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.spotted.data.sighting.SightingRepositoryFirestore
import com.android.spotted.data.trust.TrustRepositoryFirestore
import com.android.spotted.data.vote.VoteRepositoryFirestore
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.sighting.Sighting
import com.android.spotted.model.sighting.SightingRepository
import com.android.spotted.model.trust.TrustLevel
import com.android.spotted.model.trust.TrustRepository
import com.android.spotted.model.vote.AlreadyVotedException
import com.android.spotted.model.vote.VoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VoteViewModel(
    private val sightingId: String,
    private val alertId: String,
    private val voteRepository: VoteRepository,
    private val sightingRepository: SightingRepository,
    private val trustRepository: TrustRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(VoteUiState())
  val uiState: StateFlow<VoteUiState> = _uiState.asStateFlow()

  private var sighting: Sighting? = null

  init {
    load()
  }

  fun load() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      val uid = authRepository.currentUser.value?.uid
      if (uid == null) {
        _uiState.update {
          it.copy(
              isLoading = false,
              canVote = false,
              notEligibleReason = SIGN_IN_REQUIRED,
          )
        }
        return@launch
      }

      sightingRepository
          .getSightingsForAlert(alertId)
          .fold(
              onSuccess = { sightings ->
                val found = sightings.firstOrNull { it.id == sightingId }
                sighting = found
                if (found == null) {
                  _uiState.update {
                    it.copy(
                        isLoading = false,
                        canVote = false,
                        notEligibleReason = SIGHTING_NOT_FOUND,
                    )
                  }
                  return@launch
                }
                loadVoteState(uid, found)
              },
              onFailure = { _ ->
                _uiState.update {
                  it.copy(
                      isLoading = false,
                      errorMessage = LOAD_FAILED,
                  )
                }
              },
          )
    }
  }

  private suspend fun loadVoteState(uid: String, found: Sighting) {
    val alreadyVoted = voteRepository.hasVoted(sightingId, uid)
    val voteCount = voteRepository.getVoteCount(sightingId)
    val trust = trustRepository.getTrust(uid)

    val already = alreadyVoted.getOrNull() ?: false
    val notEligibleReason =
        when {
          already -> ALREADY_VOTED
          found.reporterId == uid -> OWN_SIGHTING
          trust.getOrNull()?.level in setOf(TrustLevel.NEW, TrustLevel.LOW) -> NOT_TRUSTED
          else -> null
        }

    _uiState.update {
      it.copy(
          voteCount = voteCount.getOrDefault(0),
          alreadyVoted = already,
          canVote =
              alreadyVoted.isSuccess &&
                  voteCount.isSuccess &&
                  trust.isSuccess &&
                  notEligibleReason == null,
          notEligibleReason = notEligibleReason,
          isLoading = false,
          errorMessage =
              if (alreadyVoted.isFailure || voteCount.isFailure || trust.isFailure) LOAD_FAILED
              else null,
      )
    }
  }

  fun vote() {
    val uid = authRepository.currentUser.value?.uid
    if (uid == null || !_uiState.value.canVote) return

    _uiState.update { it.copy(errorMessage = null) }
    viewModelScope.launch {
      voteRepository
          .castVote(sightingId, uid)
          .fold(
              onSuccess = {
                _uiState.update {
                  it.copy(
                      voteCount = it.voteCount + 1,
                      alreadyVoted = true,
                      canVote = false,
                      notEligibleReason = ALREADY_VOTED,
                  )
                }
              },
              onFailure = { failure ->
                val reason = if (failure is AlreadyVotedException) ALREADY_VOTED else null
                _uiState.update {
                  it.copy(
                      alreadyVoted = failure is AlreadyVotedException,
                      canVote = failure !is AlreadyVotedException && it.canVote,
                      notEligibleReason = reason ?: it.notEligibleReason,
                      errorMessage =
                          if (failure is AlreadyVotedException) null
                          else VOTE_FAILED,
                  )
                }
              },
          )
    }
  }

  companion object {
    const val SIGN_IN_REQUIRED = "Sign in to vote."
    const val SIGHTING_NOT_FOUND = "Sighting not found."
    const val ALREADY_VOTED = "You already voted on this sighting."
    const val OWN_SIGHTING = "You cannot vote on your own sighting."
    const val NOT_TRUSTED = "You are not trusted enough to vote."
    const val LOAD_FAILED = "Couldn't load the sighting. Please try again."
    const val VOTE_FAILED = "Couldn't vote. Please try again."

    val Factory: (String, String) -> ViewModelProvider.Factory = { sightingId, alertId ->
      viewModelFactory {
        initializer {
          VoteViewModel(
              sightingId = sightingId,
              alertId = alertId,
              voteRepository = VoteRepositoryFirestore(),
              sightingRepository = SightingRepositoryFirestore(),
              trustRepository = TrustRepositoryFirestore(),
              authRepository = AuthRepositoryProvider.authRepository,
          )
        }
      }
    }
  }
}
