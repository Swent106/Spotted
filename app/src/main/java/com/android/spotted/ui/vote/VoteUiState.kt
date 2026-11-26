// Portions of this code were generated with the help of Copilot.
package com.android.spotted.ui.vote

/** State for voting on a sighting. */
data class VoteUiState(
    val voteCount: Int = 0,
    val alreadyVoted: Boolean = false,
    val canVote: Boolean = false,
    val notEligibleReason: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
