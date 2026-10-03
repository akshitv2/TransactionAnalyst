package com.pulsefinance.app.core

import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

enum class ViewMode { MONTHLY, ALL_TIME }

data class LabeledValue(val label: String, val value: Double)

data class Kpis(
    val cardSpendGross: Double = 0.0,
    val cardRefunds: Double = 0.0,
    val netCardSpend: Double = 0.0,
    val totalDebits: Double = 0.0,
    val totalCredits: Double = 0.0,
    val netCashFlow: Double = 0.0,
    val topCategory: String? = null,
    val topCategoryAmount: Double = 0.0,
)

data class Summary(
    val kpis: Kpis = Kpis(),
    /** Daily debits (monthly view) or monthly debits (all-time view). */
    val trend: List<LabeledValue> = emptyList(),
    /** Debit totals per category, largest first. */
    val categories: List<LabeledValue> = emptyList(),
    /** Total volume per template / channel (debits + credits), keyed by template name. */
    val methods: List<LabeledValue> = emptyList(),
    /** Top 5 debit merchants. */
    val merchants: List<LabeledValue> = emptyList(),
)

object Analytics {

    fun inPeriod(all: List<Transaction>, mode: ViewMode, year: Int, month: Int): List<Transaction> =
        if (mode == ViewMode.ALL_TIME) all else all.filter { it.year == year && it.month == month }

    fun summarize(
        period: List<Transaction>,
        mode: ViewMode,
        year: Int,
        month: Int,
        methodOrder: List<String>,
    ): Summary {
        var cardSpends = 0.0
        var cardRefunds = 0.0
        var totalDebits = 0.0
        var totalCredits = 0.0
        val categoryDebits = LinkedHashMap<String, Double>()
        val merchantDebits = LinkedHashMap<String, Double>()
        val methodTotals = LinkedHashMap<String, Double>()
        methodOrder.forEach { methodTotals[it] = 0.0 }

        for (t in period) {
            methodTotals[t.template] = (methodTotals[t.template] ?: 0.0) + t.amount
            if (t.isDebit) {
                totalDebits += t.amount
                if (t.template.endsWith("_card_spend")) cardSpends += t.amount
                categoryDebits[t.category] = (categoryDebits[t.category] ?: 0.0) + t.amount
                merchantDebits[t.storeName] = (merchantDebits[t.storeName] ?: 0.0) + t.amount
            } else if (t.isCredit) {
                totalCredits += t.amount
                if (t.template.endsWith("_card_refund")) cardRefunds += t.amount
            }
        }

        val categories = categoryDebits.map { LabeledValue(it.key, it.value) }
            .sortedByDescending { it.value }
        val top = categories.firstOrNull()

        val kpis = Kpis(
            cardSpendGross = cardSpends,
            cardRefunds = cardRefunds,
            netCardSpend = maxOf(0.0, cardSpends - cardRefunds),
            totalDebits = totalDebits,
            totalCredits = totalCredits,
            netCashFlow = totalCredits - totalDebits,
            topCategory = top?.label,
            topCategoryAmount = top?.value ?: 0.0,
        )

        return Summary(
            kpis = kpis,
            trend = buildTrend(period, mode, year, month),
            categories = categories,
            methods = methodTotals.map { LabeledValue(it.key, it.value) },
            merchants = merchantDebits.map { LabeledValue(it.key.ifBlank { "Unknown" }, it.value) }
                .sortedByDescending { it.value }
                .take(5),
        )
    }

    private fun buildTrend(
        period: List<Transaction>,
        mode: ViewMode,
        year: Int,
        month: Int,
    ): List<LabeledValue> {
        if (mode == ViewMode.MONTHLY) {
            val days = YearMonth.of(year, month).lengthOfMonth()
            val totals = DoubleArray(days)
            period.filter { it.isDebit }.forEach { totals[it.day - 1] += it.amount }
            val abbr = monthAbbr(month)
            return totals.mapIndexed { i, v -> LabeledValue("${i + 1} $abbr", v) }
        }
        val byMonth = java.util.TreeMap<YearMonth, Double>()
        period.filter { it.isDebit }.forEach {
            val key = YearMonth.of(it.year, it.month)
            byMonth[key] = (byMonth[key] ?: 0.0) + it.amount
        }
        return byMonth.map { (ym, v) -> LabeledValue("${monthAbbr(ym.monthValue)} ${ym.year}", v) }
    }

    // ---- labels ---------------------------------------------------------------------------

    fun monthName(month: Int): String =
        Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    fun monthAbbr(month: Int): String =
        Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    private val FULL_LABELS = mapOf(
        "hdfc_card_spend" to "HDFC Card Spend",
        "icici_card_spend" to "ICICI Card Spend",
        "kotak_upi_transfer" to "Kotak UPI Transfer",
        "hdfc_upi_transfer" to "HDFC UPI Transfer",
        "hdfc_ac_autodebit" to "HDFC Auto Debit",
        "hdfc_card_refund" to "HDFC Card Refund",
    )

    private val SHORT_LABELS = mapOf(
        "hdfc_card_spend" to "HDFC Card",
        "icici_card_spend" to "ICICI Card",
        "kotak_upi_transfer" to "Kotak UPI",
        "hdfc_upi_transfer" to "HDFC UPI",
        "hdfc_ac_autodebit" to "Auto Debit",
        "hdfc_card_refund" to "Card Refund",
    )

    private val ACRONYMS = setOf("hdfc", "icici", "sbi", "upi", "kotak", "axis", "atm", "emi")

    /** Friendly label for a template name; templates you add later get a sensible fallback. */
    fun methodLabel(name: String): String = FULL_LABELS[name] ?: prettify(name)

    fun methodShortLabel(name: String): String = SHORT_LABELS[name] ?: prettify(name)

    private fun prettify(name: String): String =
        name.split('_').filter { it.isNotEmpty() }.joinToString(" ") { word ->
            if (word.lowercase() in ACRONYMS && word.lowercase() != "kotak") word.uppercase()
            else word.replaceFirstChar { it.uppercase() }
        }
}
