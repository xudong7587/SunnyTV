package io.github.xudong7587.sunnytv
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.Route
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
@OptIn(ExperimentalTestApi::class)
class BackNavigationTest {
 @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
 private var exits=0
 private lateinit var model:AppModel
 private fun mount() {
  rule.activityRule.scenario.onActivity {a->
   model=AppModel(a.application,false)
   a.setContent {CompositionLocalProvider(LocalAppModel provides model) {
    val input=LocalInputModeManager.current
    SideEffect {input.requestInputMode(InputMode.Keyboard)}
    SunnyTheme(model.settings) {SunnyRoot(onExit={exits++}) {_,_->}}
   }}
  }
 }
 @Test fun homeBackFromNavigationPromptsAndThenExits() {
  mount()
  rule.onNodeWithTag("nav:首页").performSemanticsAction(SemanticsActions.RequestFocus)
  rule.onNodeWithTag("nav:首页").performKeyInput {pressKey(Key.Back)}
  rule.onNodeWithText("再按一次返回退出软件").assertIsDisplayed()
  rule.runOnIdle {assertEquals(0,exits)}
  rule.onNodeWithTag("nav:首页").performKeyInput {pressKey(Key.Back)}
  rule.runOnIdle {assertEquals(1,exits)}
 }
 @Test fun pageBackUnwindsBeforePromptAndOtherInputDisarmsExit() {
  mount()
  rule.runOnIdle {model.navigate(Route.Libraries)}
  rule.onNodeWithTag("nav:媒体库").performSemanticsAction(SemanticsActions.RequestFocus)
  rule.onNodeWithTag("nav:媒体库").performKeyInput {pressKey(Key.Back)}
  rule.runOnIdle {assertEquals(Route.Home,model.route);assertEquals(0,exits)}
  rule.onNodeWithTag("nav:首页").performSemanticsAction(SemanticsActions.RequestFocus)
  rule.onNodeWithTag("nav:首页").performKeyInput {pressKey(Key.Back)}
  rule.onNodeWithText("再按一次返回退出软件").assertIsDisplayed()
  rule.onNodeWithTag("nav:首页").performKeyInput {pressKey(Key.DirectionRight)}
  rule.onNodeWithText("再按一次返回退出软件").assertDoesNotExist()
  rule.runOnIdle {assertEquals(0,exits)}
 }
}
