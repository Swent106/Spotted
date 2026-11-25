// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.vote

import com.android.spotted.model.sighting.Sighting

/** State for voting on sightings and viewing the owner's hidden sightings. */
data class VoteUiState(
    val voteCount: Int = 0,
    val alreadyVoted: Boolean = false,
    val canVote: Boolean = false,
    val notEligibleReason: String? = null,
    val hiddenSightings: List<Sighting> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
