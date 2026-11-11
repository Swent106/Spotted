// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.ui.linktag

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.android.spotted.R
import com.android.spotted.data.Pet.PetRepositoryFirestore
import com.android.spotted.data.photo.PhotoRepositoryFirestore
import com.android.spotted.data.tag.PetTagRepositoryFirestore
import com.android.spotted.data.user.UserRepositoryFirestore
import com.android.spotted.model.Pet.PetRepository
import com.android.spotted.model.auth.AuthRepository
import com.android.spotted.model.auth.AuthRepositoryProvider
import com.android.spotted.model.tag.PetTagRepository
import com.android.spotted.model.tag.TagAlreadyLinkedException
import com.android.spotted.model.tag.TagLinker
import com.android.spotted.model.tag.TagWriteError
import com.android.spotted.model.tag.TagWriteException
import com.android.spotted.model.tag.TagWriteOutcome
import com.android.spotted.model.tag.TagWriter
import com.android.spotted.model.user.UserProfile
import com.android.spotted.model.user.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The tag is linked, but neither its contacts could be saved nor the link undone. */
private class TagLeftLinkedException(cause: Throwable?) :
    IllegalStateException("The tag stayed linked without its contacts", cause)

/**
 * Links an NFC tag to pet [petId]: waits for a tap, writes the pet's Spotted URL on the tag, links
 * it, and saves which of the owner's contacts finders can see (phone and email only, never the
 * address).
 *
 * The [TagWriter] is passed to [start] rather than to the constructor: it is tied to the screen's
 * Activity, which a rotation replaces while this ViewModel lives on. Taking it per call means a
 * retry always uses the current screen's writer and this ViewModel never keeps an old Activity.
 */
class LinkTagViewModel(
    private val petId: String,
    private val petTagRepository: PetTagRepository,
    private val petRepository: PetRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(LinkTagUiState())
  val uiState: StateFlow<LinkTagUiState> = _uiState.asStateFlow()

  private val linker = TagLinker(petTagRepository)
  private var linkJob: Job? = null

  init {
    loadPet()
  }

  fun onShowPhoneChanged(show: Boolean) {
    if (isEditable()) _uiState.update { it.copy(showPhone = show) }
  }

  fun onShowEmailChanged(show: Boolean) {
    if (isEditable()) _uiState.update { it.copy(showEmail = show) }
  }

  /**
   * Starts linking when [LinkTagUiState.canStart]: waits for a tag tapped on [tagWriter], then
   * links it. Ends in [LinkTagStep.Success], or in [LinkTagStep.Error] with nothing linked.
   */
  fun start(tagWriter: TagWriter) {
    val state = _uiState.value
    if (!state.canStart) return
    _uiState.update { it.copy(step = LinkTagStep.WaitingForTag, errorMessage = null) }
    linkJob = viewModelScope.launch {
      val result =
          try {
            link(tagWriter, showPhone = state.showPhone, showEmail = state.showEmail)
          } catch (exception: CancellationException) {
            Result.failure(exception)
          }
      // Rethrows only if this job was really cancelled (cancel(), or the ViewModel cleared). A
      // CancellationException from a repository (e.g. a Firebase task cancelled on its side) is a
      // failure like any other: rethrowing it would leave the step stuck.
      if (result.exceptionOrNull() is CancellationException) ensureActive()
      _uiState.update {
        result.fold(
            onSuccess = { _ -> it.copy(step = LinkTagStep.Success, errorMessage = null) },
            onFailure = { error ->
              it.copy(step = LinkTagStep.Error, errorMessage = messageFor(error))
            },
        )
      }
    }
  }

  /**
   * Stops waiting for a tag, e.g. when the screen pauses, and goes back to [LinkTagStep.Idle]. Once
   * a tag was tapped, saving the link is not interrupted.
   */
  fun cancel() {
    if (_uiState.value.step != LinkTagStep.WaitingForTag) return
    linkJob?.cancel()
    linkJob = null
    _uiState.update { it.copy(step = LinkTagStep.Idle) }
  }

  private suspend fun link(
      tagWriter: TagWriter,
      showPhone: Boolean,
      showEmail: Boolean,
  ): Result<Unit> {
    // Loaded before the tap, so a missing profile fails with nothing written or linked.
    val owner =
        loadOwner().getOrElse {
          return Result.failure(it)
        }

    val write = tagWriter.writeOnNextTap(linker.newTagId())
    // Once a tag is tapped, finish even if the screen closes: stopping between linking it and
    // saving its contacts would leave it linked with contacts the owner did not choose.
    return withContext(NonCancellable) {
      _uiState.update { it.copy(step = LinkTagStep.Writing) }
      saveLink(write, owner, showPhone = showPhone, showEmail = showEmail)
    }
  }

  private suspend fun saveLink(
      write: Result<TagWriteOutcome>,
      owner: UserProfile,
      showPhone: Boolean,
      showEmail: Boolean,
  ): Result<Unit> {
    linker.link(write, petId).onFailure {
      return Result.failure(it)
    }
    val tagId = write.getOrThrow().tagId // The link succeeded, so the write did too.
    val saved = attempt {
      petTagRepository.setVisibleContacts(
          tagId = tagId,
          showPhone = showPhone,
          showEmail = showEmail,
          phone = owner.phone,
          email = owner.email,
      )
    }
    if (saved.isSuccess) return saved
    // Unlink, so nothing stays linked with contacts the owner did not choose.
    return if (attempt { petTagRepository.unlinkTag(tagId) }.isSuccess) {
      saved
    } else {
      // Known gap: the tag stays linked with the default contacts, and TagLinker refuses it on a
      // retry. Fixing it needs linkTag to save the contacts in the same write.
      Result.failure(TagLeftLinkedException(saved.exceptionOrNull()))
    }
  }

  /**
   * Runs [call] and returns what it throws as a failure, so the rollback in [saveLink] also runs
   * when a repository throws. Only called in [NonCancellable] after the tap, where even a
   * [CancellationException] comes from the repository, not from this job.
   */
  private suspend fun <T> attempt(call: suspend () -> Result<T>): Result<T> =
      try {
        call()
      } catch (exception: Exception) {
        Result.failure(exception)
      }

  private suspend fun loadOwner(): Result<UserProfile> {
    val uid =
        authRepository.currentUser.value?.uid
            ?: return Result.failure(IllegalStateException("No signed-in user"))
    return userRepository.getUser(uid)
  }

  private fun loadPet() {
    viewModelScope.launch {
      petRepository
          .getPet(petId)
          .fold(
              onSuccess = { pet -> _uiState.update { it.copy(petName = pet.name) } },
              onFailure = {
                _uiState.update {
                  it.copy(step = LinkTagStep.Error, errorMessage = R.string.link_tag_load_error)
                }
              },
          )
    }
  }

  private fun isEditable(): Boolean =
      _uiState.value.step == LinkTagStep.Idle || _uiState.value.step == LinkTagStep.Error

  @StringRes
  private fun messageFor(error: Throwable): Int =
      when (error) {
        is TagLeftLinkedException -> R.string.link_tag_contacts_not_saved_error
        is TagAlreadyLinkedException -> R.string.link_tag_already_linked_error
        is TagWriteException ->
            when (error.error) {
              TagWriteError.TAG_LOST -> R.string.link_tag_lost_error
              TagWriteError.READ_ONLY -> R.string.link_tag_read_only_error
              TagWriteError.NOT_ENOUGH_SPACE -> R.string.link_tag_not_enough_space_error
              TagWriteError.UNSUPPORTED -> R.string.link_tag_unsupported_error
              TagWriteError.NFC_UNAVAILABLE -> R.string.link_tag_nfc_unavailable_error
            }
        else -> R.string.link_tag_save_error
      }

  companion object {
    fun factory(petId: String): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
        LinkTagViewModel(
            petId = petId,
            petTagRepository = PetTagRepositoryFirestore(),
            petRepository =
                PetRepositoryFirestore(photoRepository = PhotoRepositoryFirestore(application)),
            userRepository = UserRepositoryFirestore(),
            authRepository = AuthRepositoryProvider.authRepository,
        )
      }
    }
  }
}
