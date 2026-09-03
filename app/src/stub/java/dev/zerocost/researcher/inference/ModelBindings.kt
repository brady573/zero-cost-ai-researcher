package dev.zerocost.researcher.inference

import android.content.Context
import dev.zerocost.researcher.config.AppPreferences
import dev.zerocost.researcher.performance.ModelTelemetryRecorder

object ModelBindings {
    fun create(
        context: Context,
        preferences: AppPreferences,
        telemetry: ModelTelemetryRecorder,
    ): ResearchModel = UnavailableResearchModel
}

private object UnavailableResearchModel : ResearchModel {
    override suspend fun generateText(
        systemPrompt: String,
        prompt: String,
        maxTokens: Int,
    ): String {
        throw ModelNotReadyException(
            "Stub build: run scripts/bootstrap-llama.sh and build the llama flavor."
        )
    }
}
