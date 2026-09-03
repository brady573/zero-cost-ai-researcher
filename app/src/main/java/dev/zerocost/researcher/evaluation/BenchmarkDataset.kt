package dev.zerocost.researcher.evaluation

import android.content.Context
import org.json.JSONObject

class BenchmarkDataset(private val context: Context) {
    fun load(): List<BenchmarkQuestion> {
        val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val array = JSONObject(text).getJSONArray("questions")

        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val facetsJson = item.getJSONArray("facets")
                val facets = buildList {
                    for (facetIndex in 0 until facetsJson.length()) {
                        add(facetsJson.getString(facetIndex))
                    }
                }

                add(
                    BenchmarkQuestion(
                        id = item.getString("id"),
                        category = BenchmarkCategory.valueOf(item.getString("category")),
                        question = item.getString("question"),
                        requiresFreshness = item.getBoolean("requiresFreshness"),
                        expectedConflict = item.getBoolean("expectedConflict"),
                        facets = facets,
                    )
                )
            }
        }
    }

    fun selectStratified(limit: Int): List<BenchmarkQuestion> {
        val all = load()
        val byCategory = BenchmarkCategory.entries.associateWith { category ->
            all.filter { it.category == category }
        }

        val selected = mutableListOf<BenchmarkQuestion>()
        var round = 0
        while (selected.size < limit) {
            var added = false
            for (category in BenchmarkCategory.entries) {
                val candidate = byCategory.getValue(category).getOrNull(round) ?: continue
                selected += candidate
                added = true
                if (selected.size == limit) break
            }
            if (!added) break
            round++
        }
        return selected
    }

    companion object {
        private const val ASSET_PATH = "benchmark/questions.json"
    }
}
