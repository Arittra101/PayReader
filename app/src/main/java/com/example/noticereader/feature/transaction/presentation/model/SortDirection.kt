package com.example.noticereader.feature.transaction.presentation.model

/**
 * List sort order, by payment time (§10.4). Newest first is the default every time the app opens;
 * the operator can flip it with the icon button next to the date filter.
 */
enum class SortDirection {
    NEWEST_FIRST,
    OLDEST_FIRST,
}
