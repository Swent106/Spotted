// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.fakes

import com.android.spotted.model.notification.NotificationRepository
import com.android.spotted.model.notification.SightingNotification
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [NotificationRepository] for ViewModel tests.
 * - Each owner has their own list, so tests can check that switching user never mixes them.
 * - [setPending] / [setFailure] change what an owner's flow emits, live.
 * - An owner with nothing set has no pending sightings.
 * - [observedOwners]: every owner observed, in order.
 */
class FakeNotificationRepository : NotificationRepository {
  private val byOwner =
      MutableStateFlow<Map<String, Result<List<SightingNotification>>>>(emptyMap())

  val observedOwners = mutableListOf<String>()

  fun setPending(ownerId: String, notifications: List<SightingNotification>) {
    byOwner.update { it + (ownerId to Result.success(notifications)) }
  }

  fun setFailure(ownerId: String, error: Throwable) {
    byOwner.update { it + (ownerId to Result.failure(error)) }
  }

  override fun observePendingSightings(ownerId: String): Flow<Result<List<SightingNotification>>> {
    observedOwners += ownerId
    return byOwner.map { it[ownerId] ?: Result.success(emptyList()) }.distinctUntilChanged()
  }
}
