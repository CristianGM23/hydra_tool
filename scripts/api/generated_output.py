"""Deterministic, atomic writes for version-controlled HYDRA API outputs.

Never touch a file whose bytes already equal the generated contract. This is
especially important for Windows workspaces where IDEs, indexers or filesystem
filters can temporarily refuse opening a generated .ts file for truncation.
"""

from __future__ import annotations

import os
import tempfile
import time
from pathlib import Path


_WINDOWS_SHARING_ERRORS = frozenset({5, 32, 33})


def _sharing_violation(exc: OSError) -> bool:
    return os.name == "nt" and getattr(exc, "winerror", None) in _WINDOWS_SHARING_ERRORS


def write_generated(path: Path, content: str, *, retries: int = 7) -> bool:
    """Write UTF-8/LF bytes only when changed; return True if actually updated.

    Changed outputs are staged in the destination directory and committed with
    os.replace, so a failed write never leaves a truncated canonical source.
    Persistent Windows sharing violations fail explicitly after bounded retries.
    """
    path = Path(path)
    if path.is_symlink():
        raise ValueError(f"Refusing generated output symlink: {path}")
    expected = content.encode("utf-8")
    try:
        if path.read_bytes() == expected:
            return False
    except FileNotFoundError:
        pass

    staged: Path | None = None
    try:
        fd, tmp_name = tempfile.mkstemp(prefix=f".{path.name}.", suffix=".tmp", dir=path.parent)
        staged = Path(tmp_name)
        with os.fdopen(fd, "wb") as stream:
            stream.write(expected)
            stream.flush()
            os.fsync(stream.fileno())
        for attempt in range(retries + 1):
            try:
                # Re-check after staging: another generator may have completed.
                if path.is_symlink():
                    raise ValueError(f"Refusing generated output symlink: {path}")
                try:
                    if path.read_bytes() == expected:
                        return False
                except FileNotFoundError:
                    pass
                os.replace(staged, path)
                return True
            except OSError as exc:
                if attempt == retries or not _sharing_violation(exc):
                    raise
                time.sleep(min(0.05 * (attempt + 1), 0.4))
    finally:
        if staged is not None:
            staged.unlink(missing_ok=True)
