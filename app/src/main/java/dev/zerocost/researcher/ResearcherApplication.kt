package dev.zerocost.researcher

import android.app.Application

class ResearcherApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
