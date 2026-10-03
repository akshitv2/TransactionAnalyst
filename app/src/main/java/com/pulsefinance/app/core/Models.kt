package com.pulsefinance.app.core

import java.time.LocalDateTime

/** A raw SMS as read from the device inbox. */
data class SmsMessage(
    val address: String?,
    val timestampMs: Long,
    val body: String,
)

/** A compiled rule from templates.json. */
data class Template(
    val name: String,
    val type: String,
    val example: String,
    val pattern: Regex,
)

/** One parsed + categorised transaction. [month] is 1..12. */
data class Transaction(
    val id: Long,
    val timestampMs: Long,
    val template: String,
    val type: String,
    val amount: Double,
    val storeName: String,
    val category: String,
    val dateTime: LocalDateTime,
) {
    val isDebit: Boolean get() = type == "debit"
    val isCredit: Boolean get() = type == "credit"
    val year: Int get() = dateTime.year
    val month: Int get() = dateTime.monthValue
    val day: Int get() = dateTime.dayOfMonth
}

data class ParseResult(
    val transactions: List<Transaction>,
    val scannedMessages: Int,
)
