package dev.zerocost.researcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.zerocost.researcher.ui.ResearchApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ResearcherApplication).container
        setContent { ResearchApp(container) }
    }
}
