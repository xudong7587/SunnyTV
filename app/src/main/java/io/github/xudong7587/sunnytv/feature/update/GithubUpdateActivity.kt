package io.github.xudong7587.sunnytv.feature.update

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xudong7587.sunnytv.feature.ui.SunnyColors

/** Same two-action updater as settings; retained for existing activity entry points. */
class GithubUpdateActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {Column(Modifier.fillMaxSize().background(SunnyColors.Background).padding(24.dp)) {
            GithubUpdatePanel()
        }}
    }
}
