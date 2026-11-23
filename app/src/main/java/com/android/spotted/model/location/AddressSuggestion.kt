// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.location

import com.android.spotted.model.Location

/** A place proposed while the user types an address: its readable [label] and its [location]. */
data class AddressSuggestion(
    val label: String,
    val location: Location,
)
