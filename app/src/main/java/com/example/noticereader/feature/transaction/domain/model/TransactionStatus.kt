package com.example.noticereader.feature.transaction.domain.model

/**
 * The lifecycle status of a stored transaction (requirment.md §7.3, §8.2, §10.3).
 *
 *  - [PENDING]       saved locally, not yet accepted by the backend. The normal first state.
 *  - [SUCCESS]       the backend accepted the upload.
 *  - [FAILED]        an upload attempt failed; it will be retried. The row is never deleted.
 *  - [PARSING_ERROR] the SMS arrived and passed the filter, but a required field could not
 *                    be read. A human must handle the payment by hand.
 */
enum class TransactionStatus {
    PENDING,
    SUCCESS,
    FAILED,
    PARSING_ERROR,
}
