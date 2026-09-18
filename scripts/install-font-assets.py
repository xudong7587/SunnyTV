#!/usr/bin/env python3
"""Install the user's local font files into SunnyTV assets after checksum verification.

This script contains no font data. It only copies files already present on the user's
machine, so the repository cannot accidentally substitute a different font.
"""
from __future__ import annotations
import hashlib
import shutil
import sys
from pathlib import Path

EXPECTED = [
    ("fz_zhenghei.ttf", "a92f243f92a8af7b1b9b0f31782b965bd9882a7331e88b10d3b2100d0941fd94"),
    ("fz_youhei.ttf", "2050762a1c6478d1ef85208013cd2d72cf3248c8f50b02ef91ec3d4570a0f03a"),
    ("coca_cola_care.ttf", "2c1075fddb3445501e9f7b3fd4ed01c796f2ac90cffe55059b45003ad3701192"),
]

if len(sys.argv) != 4:
    raise SystemExit("usage: install-font-assets.py <方正正准黑.ttf> <方正悠黑.ttf> <可口可乐在乎体.ttf>")

root = Path(__file__).resolve().parents[1]
dest = root / "app/src/main/assets/fonts"
dest.mkdir(parents=True, exist_ok=True)
for source, (name, expected) in zip(map(Path, sys.argv[1:]), EXPECTED):
    data = source.read_bytes()
    actual = hashlib.sha256(data).hexdigest()
    if actual != expected:
        raise SystemExit(f"{source.name}: SHA-256 mismatch; refusing to copy")
    (dest / name).write_bytes(data)
    print(f"installed {name} ({len(data)} bytes)")
print("All three font assets verified and installed.")
