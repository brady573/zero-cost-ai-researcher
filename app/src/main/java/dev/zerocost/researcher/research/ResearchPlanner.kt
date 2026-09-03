package dev.zerocost.researcher.research

import dev.zerocost.researcher.inference.ResearchModel
import java.util.UUID

class ResearchPlanner(private val model: ResearchModel) {
    suspend fun createPlan(question: String, tracker: RunBudgetTracker): ResearchPlan {
        tracker.consumeModelCall()
        val json = model.generateObject(
            systemPrompt = SYSTEM,
            prompt = """
                Question: $question

                Return only JSON:
                {
                  "intent": "factual|comparison|technical|news|other",
                  "requiresFreshness": true,
                  "subquestions": [
                    {"question": "...", "priority": 0.0}
                  ]
                }

                Create 3-8 non-overlapping subquestions. Priority is 0..1.
            """.trimIndent(),
            maxTokens = 900,
        )

        val array = json.optJSONArray("subquestions")
            ?: error("Planner did not return subquestions")
        val subquestions = buildList {
            for (index in 0 until minOf(array.length(), 8)) {
                val item = array.optJSONObject(index) ?: continue
                val text = item.optString("question").trim()
                if (text.isBlank()) continue
                add(
                    PlannedSubquestion(
                        id = UUID.randomUUID().toString(),
                        question = text,
                        priority = item.optDouble("priority", 0.5).coerceIn(0.0, 1.0),
                    )
                )
            }
        }.toMutableList()

        fallbackSubquestions(question).forEach {
            if (subquestions.size < 3) subquestions += it
        }

        return ResearchPlan(
            question = question,
            intent = json.optString("intent", "other"),
            requiresFreshness = json.optBoolean("requiresFreshness", false),
            subquestions = subquestions.take(8).toMutableList(),
        )
    }

    private fun fallbackSubquestions(question: String): List<PlannedSubquestion> = listOf(
        PlannedSubquestion(
            UUID.randomUUID().toString(),
            "What core factual evidence directly bears on: $question",
            1.0,
        ),
        PlannedSubquestion(
            UUID.randomUUID().toString(),
            "What primary or authoritative sources directly address: $question",
            0.9,
        ),
        PlannedSubquestion(
            UUID.randomUUID().toString(),
            "What credible evidence would contradict or materially qualify: $question",
            0.8,
        ),
    )

    companion object {
        private const val SYSTEM = """
            You are a research planner. Decompose the user's question without answering it.
            Prefer subquestions whose resolution can change the final conclusion.
            Mark time-sensitive questions as requiring freshness.
            Never invent evidence or citations.
        """
    }
}
