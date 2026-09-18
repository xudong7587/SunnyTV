#!/usr/bin/env python3
"""One-shot dev14->dev15 edits guarded by exact Git blob IDs; never restore a snapshot."""
from pathlib import Path
import hashlib
ROOT = Path(__file__).resolve().parents[1]
UI = 'app/src/main/java/io/github/xudong7587/sunnytv/feature/ui/'
EXPECTED = {
    UI+'Components.kt': '1d3bc3b0b8e93616142ea9f1e04ac55a9856bc9b',
    UI+'HomeHeroContent.kt': '332cb165d9ab50f9c57fed2f686482a840868267',
    UI+'Motion.kt': 'afc476db9fbf8a3087f9dc725fe5f376d4be083b',
    UI+'TvAccordion.kt': '7c0985249d8d22e2464a9d4b67422831b9c780d1',
    UI+'CinemaComponents.kt': '1e255ae6d5dd5e6ffbd1432acd8084123cade9e5',
    'app/build.gradle.kts': '4487cd3c790039d990e26a1172dc03aa8b8092b4',
    'AGENTS.md': 'f189454f2ae394766979d7415e0008625c3f4677',
}
def edit(text, old, new, count=1):
    if text.count(old) != count:
        raise RuntimeError(f'Unexpected edit anchor: {old[:120]!r}')
    return text.replace(old, new)
def apply():
    if 'versionName = "0.1.0-dev15"' in (ROOT/'app/build.gradle.kts').read_text():
        assert 'reserveFocusSpace=false' in (ROOT/(UI+'HomeHeroContent.kt')).read_text()
        assert 'focusLift:Boolean=true' in (ROOT/(UI+'Components.kt')).read_text()
        print('dev15 edits already committed; no overwrite.')
        return
    changed = {}
    for name, sha in EXPECTED.items():
        data=(ROOT/name).read_bytes()
        actual=hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
        if actual!=sha: raise RuntimeError(f'Upstream changed: {name}; refusing to overwrite.')
        changed[name]=data.decode('utf-8')
    name=UI+'Components.kt'
    text=edit(changed[name], 'button:Boolean=false, restoreFocus:Boolean=true,onFocus:',
              'button:Boolean=false, restoreFocus:Boolean=true,focusLift:Boolean=true,onFocus:')
    changed[name]=edit(text, '.focusElevation(shape,showShadow)', '.focusElevation(shape,showShadow,liftEnabled=focusLift)')
    name=UI+'Motion.kt'
    text=edit(changed[name], 'verticalAlignment:Alignment.Vertical=Alignment.Top,content:LazyListScope.()->Unit)',
              'verticalAlignment:Alignment.Vertical=Alignment.Top,reserveFocusSpace:Boolean=true,content:LazyListScope.()->Unit)')
    changed[name]=edit(text, 'val safePadding=PaddingValues(', 'val safePadding=if(!reserveFocusSpace) contentPadding else PaddingValues(')
    name=UI+'HomeHeroContent.kt'
    text=edit(changed[name], 'focusModifier,shape=RoundedCornerShape(18.dp),',
              'focusModifier,shape=RoundedCornerShape(18.dp),focusLift=false,')
    text=edit(text, 'StableLazyRow(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(7.dp))',
              'StableLazyRow(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(7.dp),reserveFocusSpace=false)')
    changed[name]=edit(text, 'shape=RoundedCornerShape(13.dp),focusOutline=false,',
              'shape=RoundedCornerShape(13.dp),focusOutline=false,focusLift=false,')
    name=UI+'TvAccordion.kt'
    changed[name]=edit(changed[name], 'shape=RoundedCornerShape(15.dp),restoreFocus=false,',
                       'shape=RoundedCornerShape(15.dp),focusLift=false,restoreFocus=false,')
    name=UI+'CinemaComponents.kt'
    changed[name]=edit(changed[name], 'active=active,shape=RoundedCornerShape(if(hero) 20.dp else 15.dp),',
                       'active=active,focusLift=false,shape=RoundedCornerShape(if(hero) 20.dp else 15.dp),')
    name='app/build.gradle.kts'
    changed[name]=edit(edit(changed[name], 'versionCode = 14', 'versionCode = 15'),
                       'versionName = "0.1.0-dev14"', 'versionName = "0.1.0-dev15"')
    changed['AGENTS.md']+='\n## Signing baseline from dev15 onward\n\nThe user explicitly chose the published dev14 signing identity as the permanent upgrade baseline. Read docs/SIGNING.md. Never generate a replacement key as a fallback. Publishing an APK requires scripts/verify-release-signer.py to pass against the pinned dev14 APK; a missing secret or certificate mismatch must fail the release. Keep the current application ID and never replace an existing release tag or asset silently.\n'
    for name,text in changed.items():
        (ROOT/name).write_text(text,encoding='utf-8',newline='\n')
        print('Updated',name)
    print('Patched seven files; source adapters, playback and credentials unchanged.')
if __name__=='__main__': apply()
