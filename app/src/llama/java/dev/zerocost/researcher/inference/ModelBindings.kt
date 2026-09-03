package dev.zerocost.researcher.inference

import android.content.Context
import dev.zerocost.researcher.config.AppPreferences
import dev.zerocost.researcher.performance.ModelTelemetryRecorder

object ModelBindings {
    fun create(
        context: Context,
        preferences: AppPreferences,
        telemetry: ModelTelemetryRecorder,
    ): ResearchModel =
        LlamaCppResearchModel(
            context = context.applicationContext,
            preferences = preferences,
            telemetry = telemetry,
        )
}
