package com.example.noticereader.feature.transaction.presentation.model

/**
 * The three tabs of the single screen (§10.3). Order matters — it is the pager order.
 */
enum class TransactionTab(val label: String, val emptyText: String) {
    ALL(label = "All", emptyText = "No transactions yet"),
    SUCCESS(label = "Success", emptyText = "No confirmed payments yet"),
    FAILED(label = "Failed", emptyText = "No failed payments"),
}
