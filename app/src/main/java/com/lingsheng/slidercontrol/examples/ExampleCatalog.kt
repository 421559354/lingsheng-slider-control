package com.lingsheng.slidercontrol.examples

import android.content.Context
import org.json.JSONObject

object ExampleCatalog {
    fun load(context: Context): List<ExampleEntry> {
        val json = context.assets.open("examples/index.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val entries = JSONObject(json).getJSONArray("entries")
        return List(entries.length()) { index ->
            val item = entries.getJSONObject(index)
            ExampleEntry(
                id = item.getString("id"),
                title = item.getString("title"),
                sourcePath = item.getString("sourcePath"),
                category = item.getString("category"),
                summary = item.getString("summary"),
                textAsset = item.getString("textAsset"),
            )
        }
    }

    fun loadText(context: Context, entry: ExampleEntry): String =
        context.assets.open(entry.textAsset).bufferedReader(Charsets.UTF_8).use { it.readText() }

    fun search(
        entries: List<ExampleEntry>,
        query: String,
        category: String?,
        sourceTextById: Map<String, String> = emptyMap(),
    ): List<ExampleEntry> {
        val needle = query.trim()
        return entries.filter { entry ->
            (category == null || entry.category == category) &&
                (needle.isBlank() || entry.title.contains(needle, ignoreCase = true) ||
                    entry.summary.contains(needle, ignoreCase = true) ||
                    entry.sourcePath.contains(needle, ignoreCase = true) ||
                    sourceTextById[entry.id]?.contains(needle, ignoreCase = true) == true)
        }
    }
}
