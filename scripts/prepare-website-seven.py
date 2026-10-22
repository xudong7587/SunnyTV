"""Prepare ignored Android capture fixtures from the public website art. Run from repo root."""
from pathlib import Path
import shutil
root=Path(__file__).resolve().parents[1]
source=root/'website/assets/cinema'
target=root/'app/src/androidTest/assets/showcase-seven'
target.mkdir(parents=True,exist_ok=True)
for index,slug in enumerate(('animation','romance','fantasy','scifi','noir','wuxia','horror')):
    for kind in ('poster','wide','logo'):
        shutil.copy2(source/f'{slug}-{kind}.webp',target/f'{index}-{kind}.webp')
print('Prepared 21 public demo artwork fixtures.')
