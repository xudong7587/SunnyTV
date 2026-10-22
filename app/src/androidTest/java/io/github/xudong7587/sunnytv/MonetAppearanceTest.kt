package io.github.xudong7587.sunnytv

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.xudong7587.sunnytv.core.model.*
import io.github.xudong7587.sunnytv.core.storage.ConfigStore
import io.github.xudong7587.sunnytv.feature.AppModel
import io.github.xudong7587.sunnytv.feature.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalTestApi::class)
class MonetAppearanceTest {
 @get:Rule val rule=createAndroidComposeRule<ComponentActivity>()
 @Test fun preferencesPersistAndUnknownValuesRecoverWithoutTouchingManualColor() {
  val context=object:ContextWrapper(rule.activity) {
   override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences("monet-fixture",mode)
  }
  val prefs=context.getSharedPreferences("fixture",Context.MODE_PRIVATE);prefs.edit().clear().commit()
  try {
   val store=ConfigStore(context)
   val setting=AppSettings(appearanceColor="custom",customThemeColor=0xFF27A3B5,monetEnabled=true,monetSource="palette",monetStyle="expressive")
   store.saveSettings(setting)
   val saved=ConfigStore(context).settings()
   assertTrue(saved.monetEnabled);assertEquals("palette",saved.monetSource);assertEquals("expressive",saved.monetStyle)
   assertEquals("custom",saved.appearanceColor);assertEquals(0xFF27A3B5L,saved.customThemeColor)
   prefs.edit().putString("monetSource","bad").putString("monetStyle","bad").commit()
   assertEquals("system",store.settings().monetSource);assertEquals("tonal_spot",store.settings().monetStyle)
  } finally {prefs.edit().clear().commit()}
 }
 @Test fun systemColorReaderUsesAvailableRolesAndOldAndroidHasDeterministicFallback() {
  val fallback=readSystemMonet(rule.activity,true,30)
  assertFalse(fallback.available);assertEquals(MonetPolicy.FALLBACK_SEED,fallback.seed)
  val actual=readSystemMonet(rule.activity,true)
  if(Build.VERSION.SDK_INT>=31) assertTrue(actual.available) else assertFalse(actual.available)
  assertEquals(0xFFL,actual.seed ushr 24)
 }
 @Test fun remoteCanEnablePickSourceAndStyleThenReachMaterials() {
  lateinit var model:AppModel
  rule.activityRule.scenario.onActivity {a->
   model=AppModel(a.application,false)
   model.saveSettings(AppSettings(reduceMotion=true,appearanceColor="rose"))
   a.setContent {
    val input=LocalInputModeManager.current
    SideEffect {input.requestInputMode(InputMode.Keyboard)}
    CompositionLocalProvider(LocalAppModel provides model,LocalCompact provides false) {
     SunnyTheme(model.settings) {Box(Modifier.fillMaxSize().background(SunnyColors.Background)) {SettingsScreen()}}
    }
   }
  }
  rule.onNodeWithTag("settings:主题").performClick()
  rule.onNodeWithTag("settings:viewport").performScrollToNode(hasTestTag("toggle:Monet 动态取色"))
  rule.onNodeWithTag("toggle:Monet 动态取色").performSemanticsAction(SemanticsActions.RequestFocus)
  rule.onNodeWithTag("toggle:Monet 动态取色").performKeyInput {pressKey(Key.DirectionCenter)}
  rule.runOnIdle {assertTrue(model.settings.monetEnabled)}
  rule.onNodeWithTag("settings:viewport").performScrollToNode(hasTestTag("setting:取色来源"))
  rule.onNodeWithTag("setting:取色来源").performClick()
  rule.onNodeWithTag("dialog:取色来源:palette").performClick()
  rule.onNodeWithTag("settings:viewport").performScrollToNode(hasTestTag("setting:色彩倾向"))
  rule.onNodeWithTag("setting:色彩倾向").performClick()
  rule.onNodeWithTag("dialog:色彩倾向:fruit_salad").assertIsDisplayed().performClick()
  rule.onNodeWithTag("setting:色彩倾向").performClick()
  rule.waitUntil(5_000) {rule.onAllNodes(hasTestTag("dialog:色彩倾向:fruit_salad") and isFocused()).fetchSemanticsNodes().isNotEmpty()}
  rule.onNodeWithTag("dialog:色彩倾向:fruit_salad").assertIsFocused()
  rule.onNodeWithTag("dialog:色彩倾向:vibrant").performClick()
  rule.runOnIdle {assertEquals("palette",model.settings.monetSource);assertEquals("vibrant",model.settings.monetStyle);assertEquals("rose",model.settings.appearanceColor)}
  rule.onNodeWithTag("settings:viewport").performScrollToNode(hasTestTag("monet:tones"))
  rule.onNodeWithTag("monet:tones").assertIsDisplayed()
  val screenshot=rule.onRoot().captureToImage().asAndroidBitmap()
  File(rule.activity.cacheDir,"monet-settings.png").outputStream().use {screenshot.compress(Bitmap.CompressFormat.PNG,100,it)}
  rule.onNodeWithTag("settings:viewport").performScrollToNode(hasTestTag("material:soft"))
  rule.onNodeWithTag("material:soft").performSemanticsAction(SemanticsActions.RequestFocus)
  for(id in listOf("soft","paper","duotone","outline","clay")) {
   rule.onNodeWithTag("material:$id").performKeyInput {pressKey(Key.DirectionDown)};rule.waitForIdle()
  }
  rule.onNodeWithTag("material:cutout").assertIsFocused()
 }
}
