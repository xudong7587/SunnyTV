"""Inspect approved seed colors without overwriting the native algorithm/custom picker.

Usage: python scripts/import-sunny-appearance.py <sunny-ui-design/assets/appearance>
2026-10-21: the user replaced CSS auxiliary colors with a native monochrome tonal algorithm.
This importer is now read-only; its output is for reviewing future source changes.
"""
import json,re,sys
from pathlib import Path
source=Path(sys.argv[1])
css=(source/"preview.css").read_text(encoding="utf-8-sig")
presets=(source/"presets.ts").read_text(encoding="utf-8-sig")
entries=json.loads(re.search(r"export const single = (\[.*?\]) as const;",presets,re.S)[1])
for entry in entries:
    ident=entry["id"]
    if ident in {"green","mustard","slate"}: continue
    block=re.search(r'html\[data-color="'+ident+r'"\]\{([^}]+)\}',css)[1]
    seed=re.search(r"--accent:(#[0-9a-fA-F]{6})",block)[1]
    print(ident,entry["name"],seed)
