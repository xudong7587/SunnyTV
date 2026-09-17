"""Restore a checksum-verified SunnyTV seed into a newly created repository.

This module has no network or Git writes. CI decides whether/when to commit.
It refuses to overwrite an existing app or modify .git/.github or signing keys.
"""
from __future__ import annotations

import hashlib
import io
import stat
import zipfile
from pathlib import Path, PurePosixPath

MAX_ARCHIVE_BYTES = 4 * 1024 * 1024
MAX_EXPANDED_BYTES = 12 * 1024 * 1024
MAX_FILES = 512
REQUIRED = {"settings.gradle.kts", "app/build.gradle.kts", "AGENTS.md"}


def restore_zip(payload: bytes, target: Path, expected_sha256: str) -> str:
    target = target.resolve()
    if len(payload) > MAX_ARCHIVE_BYTES:
        raise ValueError("Seed archive too large")
    if hashlib.sha256(payload).hexdigest() != expected_sha256:
        raise ValueError("Seed SHA-256 mismatch; no files were written")
    target.mkdir(parents=True, exist_ok=True)
    if (target / "app/build.gradle.kts").exists():
        return "already-initialized"

    with zipfile.ZipFile(io.BytesIO(payload)) as archive:
        members = archive.infolist()
        if not members or len(members) > MAX_FILES:
            raise ValueError("Invalid file count")
        if sum(m.file_size for m in members) > MAX_EXPANDED_BYTES:
            raise ValueError("Expanded seed too large")
        prepared: list[tuple[Path, bytes, int]] = []
        names: set[str] = set()
        for member in members:
            name = member.filename
            path = PurePosixPath(name)
            if member.is_dir() or path.is_absolute() or "\\" in name or ":" in name:
                raise ValueError("Unsupported archive member")
            if not path.parts or any(p in ("", ".", "..", ".git", ".github") for p in path.parts):
                raise ValueError("Unsafe archive path")
            if str(path) != name or name in names:
                raise ValueError("Duplicate or ambiguous archive member")
            if name.lower().endswith((".jks", ".keystore", ".p12", ".pem", ".key")) or path.name in (".env", "local.properties"):
                raise ValueError("Credentials or machine-local config are not allowed")
            mode = member.external_attr >> 16
            if stat.S_ISLNK(mode):
                raise ValueError("Archive symlinks are forbidden")
            destination = target / path
            if not destination.resolve().is_relative_to(target):
                raise ValueError("Destination escapes repository")
            if destination.is_symlink() or any(p.is_symlink() for p in destination.parents if p != target.parent):
                raise ValueError("Destination contains symlink")
            if destination.exists() and name != "README.md":
                raise ValueError("Existing file would be overwritten: " + name)
            names.add(name)
            # Read and CRC-validate every member BEFORE writing the first file.
            prepared.append((destination, archive.read(member), mode))
        if not REQUIRED.issubset(names):
            raise ValueError("Seed does not contain a SunnyTV Android project")
        # A starter README is allowed; all other pre-existing project content is refused.
        for p in target.rglob("*"):
            rel = p.relative_to(target)
            if rel.parts[0] in (".git", ".github"):
                continue
            if p.is_file() and str(rel) != "README.md":
                raise ValueError("Repository is not empty: " + str(rel))
        readme = target / "README.md"
        initial_readme = readme.read_bytes() if readme.exists() else None
        if initial_readme is not None and "docs/INITIAL_REPOSITORY_README.md" in names:
            raise ValueError("README backup path conflict")
        for destination, content, mode in prepared:
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(content)
            destination.chmod(0o755 if mode & 0o111 else 0o644)
        if initial_readme is not None:
            backup = target / "docs/INITIAL_REPOSITORY_README.md"
            backup.parent.mkdir(parents=True, exist_ok=True)
            backup.write_bytes(initial_readme)
    return "initialized"
