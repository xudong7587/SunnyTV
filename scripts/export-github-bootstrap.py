#!/usr/bin/env python3
"""Export one reviewable, self-contained Actions workflow for browser-only setup.

No request is sent to GitHub. This creates a file; the repository owner uploads it.
The embedded snapshot excludes workflows, private evaluation imagery, and secrets.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import io
import json
from pathlib import Path
import textwrap
import zipfile

ROOT = Path(__file__).resolve().parents[1]
EXCLUDE_DIRS = {'.git', '.github', '.gradle', '.kotlin', '.idea', '.sunny-tools',
                '.local-tests', '__pycache__', 'build', '.ci-logs', 'dist', 'preview'}
EXCLUDE_FILES = {'SOURCE_SHA256SUMS.txt', 'docs/source-inventory.json',
                 'docs/bootstrap-export.json', 'docs/bootstrap-workflow-test-results.json'}


def seed_files(root: Path) -> list[Path]:
    output = []
    for path in sorted(root.rglob('*')):
        relative = path.relative_to(root)
        if path.is_symlink():
            raise ValueError('Symlinks are not published: ' + str(relative))
        if not path.is_file() or set(relative.parts) & EXCLUDE_DIRS:
            continue
        if str(relative) in EXCLUDE_FILES or relative.parts[:2] == ('docs', 'screens'):
            continue
        if path.suffix.lower() in {'.jpg', '.jpeg', '.png', '.webp', '.woff', '.woff2', '.ttf', '.otf', '.pyc', '.apk', '.zip'}:
            continue
        if path.suffix.lower() in {'.jks', '.keystore', '.p12', '.pem', '.key'} or path.name in {'.env', 'local.properties'}:
            raise ValueError('Secret or local-only file is present: ' + str(relative))
        output.append(path)
    return output


def make_seed(root: Path) -> tuple[bytes, list[str]]:
    files = seed_files(root)
    result = io.BytesIO()
    names = []
    manifest = []
    with zipfile.ZipFile(result, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in files:
            name = path.relative_to(root).as_posix()
            data = path.read_bytes()
            entry = zipfile.ZipInfo(name, (2026, 9, 17, 0, 0, 0))
            entry.create_system = 3
            entry.external_attr = (0o100755 if path.suffix == '.sh' else 0o100644) << 16
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, data)
            names.append(name)
            manifest.append(hashlib.sha256(data).hexdigest() + '  ' + name)
        entry = zipfile.ZipInfo('SOURCE_SHA256SUMS.txt', (2026, 9, 17, 0, 0, 0))
        entry.create_system = 3
        entry.external_attr = 0o100644 << 16
        entry.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(entry, '\n'.join(manifest) + '\n')
        names.append('SOURCE_SHA256SUMS.txt')
    return result.getvalue(), names


def workflow_text(root: Path, payload: bytes) -> str:
    digest = hashlib.sha256(payload).hexdigest()
    module = (root / 'scripts/bootstrap_restore.py').read_text(encoding='utf-8')
    encoded = base64.b64encode(payload).decode('ascii')
    # Keep run < 21,000 chars and each env value far below OS argument limits.
    # Seed data is source code, not secrets. It is scoped to this one restore step.
    chunks = [encoded[i:i+12000] for i in range(0, len(encoded), 12000)]
    env_lines = [f'          SUNNYTV_SEED_{i:03d}: "{chunk}"' for i, chunk in enumerate(chunks)]
    env_lines += [f'          SUNNYTV_SEED_COUNT: "{len(chunks)}"', f'          SUNNYTV_SEED_SHA256: "{digest}"']
    python = module + '\n\nimport base64, os\n'
    python += 'encoded = "".join(os.environ[f"SUNNYTV_SEED_{i:03d}"] for i in range(int(os.environ["SUNNYTV_SEED_COUNT"])))\n'
    python += 'status = restore_zip(base64.b64decode(encoded, validate=True), Path.cwd(), os.environ["SUNNYTV_SEED_SHA256"])\n'
    python += 'print("SunnyTV source snapshot: " + status)\n'
    python += 'with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as result:\n'
    python += '    result.write("initialized=" + ("true" if status == "initialized" else "false") + "\\n")\n'
    shell = "python3 - <<'SUNNYTV_PY'\n" + python + 'SUNNYTV_PY\n'
    template = (root / 'scripts/bootstrap_workflow.template.yml').read_text(encoding='utf-8')
    return (template.replace('__SEED_SHA__', digest)
            .replace('__SEED_ENV__', '\n'.join(env_lines))
            .replace('__RESTORE_SCRIPT__', textwrap.indent(shell.rstrip(), '          ')))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / '.github/workflows/sunnytv.yml')
    args = parser.parse_args()
    payload, names = make_seed(ROOT)
    workflow = workflow_text(ROOT, payload)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(workflow, encoding='utf-8')
    report = {'version': '0.1.0-dev2', 'seed_sha256': hashlib.sha256(payload).hexdigest(),
              'seed_bytes': len(payload), 'workflow_bytes': len(workflow.encode('utf-8')),
              'files': names, 'remote_execution': False,
              'excluded': 'Private visual preview, screenshots, fonts, credentials, build caches and .github workflows'}
    (ROOT / 'docs/bootstrap-export.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(json.dumps({k: v for k, v in report.items() if k != 'files'}, ensure_ascii=False, indent=2))
    print('Created:', args.output)


if __name__ == '__main__':
    main()
