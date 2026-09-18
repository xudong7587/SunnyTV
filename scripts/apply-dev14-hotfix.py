#!/usr/bin/env python3
"""One-shot, blob-guarded edit of dev13. The workflow commits edits BEFORE compiling them.

Never expand a source snapshot, write credentials, or touch another repository.
The guard intentionally fails when the six affected upstream files differ.
"""
from pathlib import Path
import hashlib

ROOT = Path(__file__).resolve().parents[1]
UI = Path('app/src/main/java/io/github/xudong7587/sunnytv/feature/ui')
EXPECTED = {
    str(UI / 'BrowseScreens.kt'): '76f8261269b6f6b73ad5ea2b78f988f89228939a',
    str(UI / 'Components.kt'): '653cc6c7289d7497183bf27044fc2ef4e4f17da9',
    str(UI / 'CinemaComponents.kt'): '31fd7c757d718272b0074eb0e74e676d0e946733',
    str(UI / 'Motion.kt'): '62b9a6e008876e1a16484f876f272bf47d174619',
    str(UI / 'SettingsScreen.kt'): '9337328a945e939b5a5814d1a38f3bf41ff4ebe9',
    'app/build.gradle.kts': '768f775d22e97087d598fd41c5321116f247ce68',
}

def blob(data: bytes) -> str:
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()

def replace(text: str, before: str, after: str, count: int = 1) -> str:
    actual = text.count(before)
    if actual != count:
        raise RuntimeError(f'Edit anchor count {actual}, expected {count}: {before[:100]!r}')
    return text.replace(before, after)

def apply() -> None:
    version = (ROOT / 'app/build.gradle.kts').read_text()
    if 'versionName = "0.1.0-dev14"' in version:
        assert 'HomeFocusRegion(HomeFocusPlan.LIBRARIES)' in (ROOT / UI / 'BrowseScreens.kt').read_text()
        assert 'focusElevation(shape,showShadow)' in (ROOT / UI / 'Components.kt').read_text()
        print('dev14 source edits already present; no source was overwritten.')
        return
    originals = {}
    for name, sha in EXPECTED.items():
        data = (ROOT / name).read_bytes()
        if blob(data) != sha:
            raise RuntimeError(f'Upstream changed: {name}; refusing to overwrite it.')
        originals[name] = data.decode('utf-8')
    changed = dict(originals)
    name = str(UI / 'BrowseScreens.kt')
    start = changed[name].index('@Composable fun HomeScreen(')
    end = changed[name].index('@Composable fun LibrariesScreen()', start)
    new_home = (ROOT / 'scripts/dev14/HomeScreen.kt.txt').read_text()
    changed[name] = changed[name][:start] + new_home + changed[name][end:]
    name = str(UI / 'Components.kt')
    text = changed[name]
    old = '''fun Modifier.flatShadow(shape:Shape,enabled:Boolean):Modifier = if(!enabled) this else
    shadow(18.dp,shape,clip=false,ambientColor=Color(0x44000000),spotColor=Color(0x52000000))
        .shadow(4.dp,shape,clip=false,ambientColor=Color(0x24000000),spotColor=Color(0x32000000))
        .graphicsLayer {translationY=-2.dp.toPx();scaleX=1.018f;scaleY=1.018f}'''
    text = replace(text, old, 'fun Modifier.flatShadow(shape:Shape,enabled:Boolean):Modifier = softFocusShadow(shape,enabled)')
    text = replace(text, '    var focused by remember { mutableStateOf(false) }', '''    val homeNavigator=LocalHomeFocusNavigator.current
    val homeSection=LocalHomeFocusSection.current
    DisposableEffect(homeNavigator,homeSection,id,requester) {
        if(homeSection!=null) homeNavigator?.register(homeSection,id,requester)
        onDispose {homeNavigator?.unregister(id,requester)}
    }
    var focused by remember { mutableStateOf(false) }''')
    text = replace(text, 'val outlineWidth=if(dark) 5.dp else 2.5.dp', 'val outlineWidth=if(dark) 5.dp else 1.5.dp')
    text = replace(text, 'if(pageActive && ((restoreFocus', 'if(pageActive && homeNavigator?.moving!=true && ((restoreFocus')
    text = replace(text, 'delay(45); runCatching { requester.requestFocus() }', 'delay(45); if(homeNavigator?.moving!=true) runCatching { requester.requestFocus() }')
    text = replace(text, '.testTag(id).flatShadow(shape,showShadow)', '.testTag(id).focusElevation(shape,showShadow)')
    text = replace(text, 'model.focusMemory[page]=id; onFocus()', 'model.focusMemory[page]=id; homeNavigator?.focused(id); onFocus()')
    text = replace(text, '            val edge=shape.createOutline(size,layoutDirection,this)', '''            val edge=shape.createOutline(size,layoutDirection,this)
            val stroke=outlineWidth.toPx()
            val innerSize=androidx.compose.ui.geometry.Size((size.width-stroke).coerceAtLeast(0f),(size.height-stroke).coerceAtLeast(0f))
            val focusEdge=shape.createOutline(innerSize,layoutDirection,this)''')
    text = replace(text, 'if(focused && focusOutline) drawOutline(edge,outline,style=Stroke(outlineWidth.toPx()))', '''if(focused && focusOutline) translate(stroke/2f,stroke/2f) {
                    drawOutline(focusEdge,outline,style=Stroke(stroke))
                }''')
    changed[name] = text
    name = str(UI / 'CinemaComponents.kt')
    text = changed[name]
    text = replace(text, '    val axis=LocalTvFocusMotion.current', '    val axis=LocalTvFocusMotion.current\n    val homeNavigator=LocalHomeFocusNavigator.current')
    text = replace(text, 'if(it.isFocused) {axis.horizontal=false;scope.launch {withFrameNanos {};reveal.bringIntoView()}}', 'if(it.isFocused && homeNavigator==null) {axis.horizontal=false;scope.launch {withFrameNanos {};reveal.bringIntoView()}}')
    changed[name] = text
    name = str(UI / 'Motion.kt')
    text = changed[name]
    text = replace(text, 'import androidx.compose.ui.platform.LocalDensity', 'import androidx.compose.ui.platform.LocalDensity\nimport androidx.compose.ui.platform.LocalLayoutDirection')
    text = replace(text, '''private val HorizontalReveal=object:BringIntoViewSpec {
    override fun calculateScrollDistance(offset:Float,size:Float,containerSize:Float):Float = when {
        offset<0 -> offset
        offset+size>containerSize -> offset+size-containerSize
        else -> 0f
    }
}''', '''private class HorizontalReveal(private val gutter:Float):BringIntoViewSpec {
    override fun calculateScrollDistance(offset:Float,size:Float,containerSize:Float):Float {
        val inset=gutter.coerceAtMost(((containerSize-size)/2f).coerceAtLeast(0f))
        return when {
            offset<inset -> offset-inset
            offset+size>containerSize-inset -> offset+size-containerSize+inset
            else -> 0f
        }
    }
}''')
    text = replace(text, '    CompositionLocalProvider(LocalBringIntoViewSpec provides HorizontalReveal,content=content)', '''    val gutter=with(LocalDensity.current) {24.dp.toPx()}
    val spec=remember(gutter) {HorizontalReveal(gutter)}
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec,content=content)''')
    text = replace(text, '''    HorizontalViewport {
        LazyRow(modifier,state=state,contentPadding=contentPadding,horizontalArrangement=horizontalArrangement,''', '''    val direction=LocalLayoutDirection.current
    val safePadding=PaddingValues(
        start=maxOf(24.dp,contentPadding.calculateStartPadding(direction)),
        end=maxOf(24.dp,contentPadding.calculateEndPadding(direction)),
        top=maxOf(18.dp,contentPadding.calculateTopPadding()),
        bottom=maxOf(18.dp,contentPadding.calculateBottomPadding()))
    HorizontalViewport {
        LazyRow(modifier,state=state,contentPadding=safePadding,horizontalArrangement=horizontalArrangement,''')
    changed[name] = text
    name = str(UI / 'SettingsScreen.kt')
    changed[name] = replace(changed[name], '原创实现；本轮没有复制 Moonfin 源码或 LumiPlayer 品牌资产。', '针对电视遥控器操作与流畅浏览设计。')
    name = 'app/build.gradle.kts'
    changed[name] = replace(replace(changed[name], 'versionCode = 13', 'versionCode = 14'), 'versionName = "0.1.0-dev13"', 'versionName = "0.1.0-dev14"')
    # Compute/validate every edit before writing any file.
    for name, text in changed.items():
        if text == originals[name]:
            raise RuntimeError(f'No change produced: {name}')
    for name, text in changed.items():
        (ROOT / name).write_text(text, encoding='utf-8', newline='\n')
        print(f'Updated {name}')
    print('Six existing files updated; network/media/MP4 playback code untouched.')

if __name__ == '__main__':
    apply()
