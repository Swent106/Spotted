// Portions of this code were generated with the help of Claude (Anthropic).
package com.android.spotted.model.alert

/**
 * Returned by [AlertRepository.publishAlert] when the pet already has an open alert. A pet is
 * reported missing at most once at a time; [openAlertId] is the alert that is still open.
 */
class AlertAlreadyOpenException(val openAlertId: String) :
    IllegalStateException("The pet already has the open alert $openAlertId")
