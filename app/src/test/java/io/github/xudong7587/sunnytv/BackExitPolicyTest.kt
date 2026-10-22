package io.github.xudong7587.sunnytv
import io.github.xudong7587.sunnytv.core.model.BackExitPolicy
import io.github.xudong7587.sunnytv.core.model.BackExitPolicy.Action.*
import org.junit.Assert.*
import org.junit.Test
class BackExitPolicyTest {
 @Test fun homePromptsBeforeExit() {val p=BackExitPolicy();assertEquals(PROMPT,p.press(0,false));assertEquals(EXIT,p.press(500,false))}
 @Test fun deepStackGetsBoundedEscape() {val p=BackExitPolicy();assertEquals(BACK,p.press(0,true));assertEquals(BACK,p.press(400,true));assertEquals(PROMPT,p.press(800,true));assertEquals(EXIT,p.press(1200,true))}
 @Test fun timeoutAndOtherInputCannotAccidentallyConfirm() {val p=BackExitPolicy();assertEquals(PROMPT,p.press(0,false));assertEquals(PROMPT,p.press(2100,false));p.reset();assertEquals(BACK,p.press(2200,true))}
 @Test fun slowNavigationNeverArmsEarlyExit() {val p=BackExitPolicy();repeat(10) {assertEquals(BACK,p.press(it*2100L,true))}}
}
