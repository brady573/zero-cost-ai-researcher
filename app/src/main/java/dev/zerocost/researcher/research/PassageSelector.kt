package dev.zerocost.researcher.research

class PassageSelector {
    fun select(page: RetrievedPage, question: String, maxChars: Int = 12_000): String {
        val queryTerms = terms(question)
        val sentences = page.text.split(Regex("(?<=[.!?])\\s+"))
        val scored = sentences.mapIndexed { index, sentence ->
            Triple(terms(sentence).intersect(queryTerms).size, index, sentence)
        }.sortedWith(
            compareByDescending<Triple<Int, Int, String>> { it.first }
                .thenBy { it.second }
        )

        val selected = LinkedHashSet<String>()
        for ((_, index, _) in scored.take(28)) {
            for (neighbor in (index - 1)..(index + 1)) {
                if (neighbor in sentences.indices) selected += sentences[neighbor]
            }
        }

        val output = selected.joinToString("\n")
        return if (output.isBlank()) page.text.take(maxChars) else output.take(maxChars)
    }

    private fun terms(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
            .toSet()
}
