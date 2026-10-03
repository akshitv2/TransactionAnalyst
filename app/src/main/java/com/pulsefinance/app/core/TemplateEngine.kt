package com.pulsefinance.app.core

import java.time.Instant
import java.time.ZoneId

/**
 * Python patterns use `(?P<store_name>...)`. Java/Android regex wants `(?<name>...)`
 * and only allows letters and digits in group names (no underscores), so
 * `(?P<store_name>` becomes `(?<storename>`.
 */
object PatternCompat {
    private val NAMED_GROUP = Regex("""\(\?P<([A-Za-z][A-Za-z0-9_]*)>""")

    fun compile(pattern: String): Regex {
        val javaPattern = NAMED_GROUP.replace(pattern) { m ->
            "(?<" + m.groupValues[1].replace("_", "") + ">"
        }
        return Regex(javaPattern, RegexOption.IGNORE_CASE)
    }
}

/** Port of classify_store(): the first keyword (in file order) found inside the store name wins. */
class StoreClassifier(
    entries: List<Pair<String, String>>,
    private val defaultCategory: String = "Others",
) {
    private val keywords: List<Pair<String, String>> =
        entries.filter { it.first.isNotBlank() }.map { it.first.uppercase() to it.second }

    fun classify(storeName: String): String {
        if (storeName.isEmpty()) return defaultCategory
        val upper = storeName.uppercase()
        for ((keyword, category) in keywords) {
            if (upper.contains(keyword)) return category
        }
        return defaultCategory
    }
}

object Categories {
    /** Same rule the dashboard used: blank / NA / N/A / NULL / NONE / UNDEFINED are hidden. */
    fun isNa(category: String?): Boolean {
        val c = category?.trim()?.uppercase() ?: return true
        return c.isEmpty() || c == "NA" || c == "N/A" || c == "NULL" || c == "NONE" || c == "UNDEFINED"
    }
}

/** Port of SMSTemplateParser: the first matching template wins. */
class SmsTransactionParser(
    private val templates: List<Template>,
    private val classifier: StoreClassifier,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    val templateNames: List<String> get() = templates.map { it.name }

    fun parse(sms: SmsMessage, id: Long): Transaction? {
        for (t in templates) {
            val match = t.pattern.find(sms.body) ?: continue
            // Commas are stripped here ("1,499.00" -> 1499.0).
            val amount = namedGroup(match, "amount")
                ?.replace(",", "")
                ?.toDoubleOrNull()
                ?: continue
            val store = namedGroup(match, "storename")?.trim().orEmpty()
            return Transaction(
                id = id,
                timestampMs = sms.timestampMs,
                template = t.name,
                type = t.type.lowercase(),
                amount = amount,
                storeName = store,
                category = classifier.classify(store),
                dateTime = Instant.ofEpochMilli(sms.timestampMs).atZone(zone).toLocalDateTime(),
            )
        }
        return null
    }

    fun parseAll(messages: List<SmsMessage>): ParseResult {
        val out = ArrayList<Transaction>()
        messages.forEachIndexed { index, sms ->
            parse(sms, index.toLong())?.let(out::add)
        }
        return ParseResult(out, messages.size)
    }

    private fun namedGroup(match: MatchResult, name: String): String? =
        try {
            (match.groups as MatchNamedGroupCollection)[name]?.value
        } catch (e: IllegalArgumentException) {
            null // this template doesn't define the group
        }
}
