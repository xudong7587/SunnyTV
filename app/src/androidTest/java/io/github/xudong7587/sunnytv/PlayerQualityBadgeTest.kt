package io.github.xudong7587.sunnytv

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ProvideTextStyle
import io.github.xudong7587.sunnytv.feature.player.PlayerControl
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayerQualityBadgeTest {
    @get:Rule val rule=createComposeRule()

    @Test fun qualityBadgesRemainCompleteAndCenteredWithLargerFonts() {
        val badge=mutableStateOf("原画")
        val scale=mutableStateOf(1f)
        rule.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale.value)) {
                ProvideTextStyle(TextStyle(fontFamily=FontFamily.Serif,letterSpacing=1.sp)) {
                    PlayerControl("清晰度","quality",badge=badge.value,showFocusLabel=false) {}
                }
            }
        }
        for(fontScale in listOf(1f,1.5f,2f)) for(value in listOf("原画","4K","1080P","720P")) {
            rule.runOnIdle {scale.value=fontScale;badge.value=value}
            val layouts=mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(value,useUnmergedTree=true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) {it(layouts)}
            val layout=layouts.single()
            assertFalse("$value at $fontScale: width=${layout.didOverflowWidth}, height=${layout.didOverflowHeight}, font=${layout.layoutInput.style.fontSize}",layout.hasVisualOverflow)
            assertEquals(value.length,layout.getLineEnd(0,visibleEnd=true))
            val center=(layout.getLineLeft(0)+layout.getLineRight(0))/2f
            assertEquals(layout.size.width/2f,center,1f)
        }
    }
}
