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

# The capture harness plays a 30-second scene made from the same public artwork.
# OpenCV is only needed for this optional, manually invoked website screenshot workflow.
import cv2
image=cv2.imread(str(source/'animation-wide.webp'))
image=cv2.resize(image,(1280,720))
writer=cv2.VideoWriter(str(target/'story.mp4'),cv2.VideoWriter_fourcc(*'mp4v'),5,(1280,720))
if not writer.isOpened():
    raise RuntimeError('MPEG-4 encoder unavailable for the original-artwork screenshot fixture')
try:
    for _ in range(150):
        writer.write(image)
finally:
    writer.release()
print('Prepared original-artwork 30-second playback fixture.')
