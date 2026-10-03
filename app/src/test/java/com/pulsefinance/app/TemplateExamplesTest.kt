package com.pulsefinance.app

import com.pulsefinance.app.core.PatternCompat
import com.pulsefinance.app.core.SmsMessage
import com.pulsefinance.app.core.SmsTransactionParser
import com.pulsefinance.app.core.StoreClassifier
import com.pulsefinance.app.core.Template
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Run with `./gradlew test`. Whenever you add a template to templates.json, give it an
 * "example" and this test will tell you if the pattern actually matches it.
 */
class TemplateExamplesTest {

    // Gradle runs unit tests with the module directory (app/) as the working directory.
    private val assets = File("src/main/assets")

    private fun templates(): List<Template> {
        val arr = JSONArray(File(assets, "templates.json").readText())
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Template(
                o.getString("name"),
                o.getString("type"),
                o.optString("example", ""),
                PatternCompat.compile(o.getString("pattern")),
            )
        }
    }

    private fun parser(): SmsTransactionParser {
        val obj = JSONObject(File(assets, "store_map.json").readText())
        val entries = obj.keys().asSequence().map { it to obj.getString(it) }.toList()
        return SmsTransactionParser(templates(), StoreClassifier(entries))
    }

    private fun sms(body: String) = SmsMessage("TEST", 1_759_400_000_000L, body)

    @Test
    fun everyTemplateExampleMatchesItsOwnTemplate() {
        val p = parser()
        for (t in templates()) {
            if (t.example.isBlank()) continue
            val tx = p.parse(sms(t.example), 0L)
            assertNotNull("Example for '${t.name}' did not parse", tx)
            assertEquals("Example for '${t.name}' matched a different template", t.name, tx!!.template)
            assertTrue("Amount for '${t.name}' should be positive", tx.amount > 0.0)
            assertTrue("Store name for '${t.name}' is empty", tx.storeName.isNotEmpty())
        }
    }

    @Test
    fun amountsWithThousandsSeparatorsAreParsedFully() {
        val tx = parser().parse(
            sms("Spent Rs. 1,499.00 On HDFC Bank Card XX4012 At Amazon Pay on 10-09-2026"), 0L
        )!!
        assertEquals(1499.0, tx.amount, 0.0)
        assertEquals("Amazon Pay", tx.storeName)
        assertEquals("SHOPPING", tx.category)
    }

    @Test
    fun unrelatedMessagesAreIgnored() {
        assertNull(parser().parse(sms("Your OTP is 123456. Do not share it with anyone."), 0L))
    }
}
