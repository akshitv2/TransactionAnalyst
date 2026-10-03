package com.pulsefinance.app.data

import android.content.Context
import android.util.Log
import com.pulsefinance.app.core.PatternCompat
import com.pulsefinance.app.core.Template
import org.json.JSONArray
import org.json.JSONObject

import java.io.File

/** Loads templates.json and store_map.json. Checks internal storage first, then assets. */
object ConfigLoader {
    private const val TAG = "ConfigLoader"

    fun loadTemplates(context: Context): List<Template> {
        val arr = JSONArray(readConfig(context, "templates.json"))
        val out = ArrayList<Template>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("name", "template_$i")
            try {
                out.add(
                    Template(
                        name = name,
                        type = o.getString("type"),
                        example = o.optString("example", ""),
                        pattern = PatternCompat.compile(o.getString("pattern")),
                    )
                )
            } catch (e: Exception) {
                // One bad pattern shouldn't take the whole app down.
                Log.w(TAG, "Skipping template '$name': ${e.message}")
            }
        }
        return out
    }

    /** Order is preserved (first matching keyword wins). Checks internal storage then assets. */
    fun loadStoreMap(context: Context): List<Pair<String, String>> {
        val obj = JSONObject(readConfig(context, "store_map.json"))
        val out = ArrayList<Pair<String, String>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out.add(k to obj.getString(k))
        }
        return out
    }

    private fun readConfig(context: Context, name: String): String {
        val file = File(context.filesDir, name)
        return if (file.exists()) {
            file.readText(Charsets.UTF_8)
        } else {
            context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }
    }
}
