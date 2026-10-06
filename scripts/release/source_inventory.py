from __future__ import annotations

"""Inventario único de fuentes que define qué pertenece a una entrega reproducible de HYDRA."""

from contextlib import contextmanager
from pathlib import Path
import tempfile
import zipfile

ALLOWED_ROOTS = (
    'src', 'frontend/src', 'frontend/public', 'frontend/scripts', 'frontend/e2e',
    'frontend/config', 'scripts', 'config', 'docs', '.github', '.mvn',
    # Official transformation bridges are source-controlled build inputs while
    # distribution packages are runtime assets consumed by the trusted
    # provisioning catalogue. Keep both in reproducible source deliveries.
    'toolchains/official', 'distribution/transformation-toolchains', 'reference-projects',
)
BASE_ALLOWED_FILES = (
    'pom.xml', 'mvnw', 'mvnw.cmd',
    'frontend/package.json', 'frontend/package-lock.json', 'frontend/index.html',
    'frontend/tsconfig.json', 'frontend/tsconfig.app.json', 'frontend/tsconfig.node.json',
    'frontend/vite.config.ts', 'frontend/vitest.config.ts', 'frontend/playwright.config.ts',
    'frontend/eslint.config.js', '.gitignore', '.gitattributes', 'LICENSE', 'NOTICE', 'HELP.md', 'MANIFEST_SHA256.txt',
    'VALIDAR_HYDRA_AGRESIVO_WINDOWS.cmd',
    'VALIDAR_HYDRA_REPOSITORIOS_REALES_WINDOWS.cmd',
)
SKIP_ROOTS = {'.git', 'target', 'data', '.idea', '.vscode'}
FRONTEND_SKIP = {'node_modules', 'dist', 'coverage', '.vite'}



@contextmanager
def source_root(source: Path):
    """Yield a directory-backed source tree from either a directory or a HYDRA source ZIP."""
    source = source.resolve()
    if source.is_dir():
        yield source
        return
    if not source.is_file() or not zipfile.is_zipfile(source):
        raise ValueError(f"Unsupported HYDRA source baseline: {source}")
    with tempfile.TemporaryDirectory(prefix="hydra-source-") as temporary:
        root = Path(temporary)
        with zipfile.ZipFile(source) as archive:
            for member in archive.infolist():
                relative = Path(member.filename)
                if relative.is_absolute() or ".." in relative.parts:
                    raise ValueError(f"Unsafe path in HYDRA source ZIP: {member.filename}")
                target = (root / relative).resolve()
                if root.resolve() not in target.parents and target != root.resolve():
                    raise ValueError(f"Unsafe path in HYDRA source ZIP: {member.filename}")
            archive.extractall(root)
        yield root

def excluded(relative: Path) -> bool:
    parts = relative.parts
    if parts and parts[0] in SKIP_ROOTS:
        return True
    if '__pycache__' in parts or relative.suffix == '.pyc':
        return True
    return len(parts) >= 2 and parts[0] == 'frontend' and parts[1] in FRONTEND_SKIP


def release_allowed_files(version: str) -> tuple[str, ...]:
    return BASE_ALLOWED_FILES + (
        f'HYDRA_V{version}_RELEASE_NOTES.md',
        f'VALIDAR_HYDRA_V{version}_WINDOWS.cmd',
    )


def source_files(root: Path, version: str) -> list[Path]:
    files: list[Path] = []
    for relative_root in ALLOWED_ROOTS:
        base = root / relative_root
        if not base.exists():
            continue
        for path in base.rglob('*'):
            if path.is_file() and not path.is_symlink() and not excluded(path.relative_to(root)):
                files.append(path)
    for relative in release_allowed_files(version):
        path = root / relative
        if path.is_file() and not path.is_symlink():
            files.append(path)
    # Root-level release notes/validators from a previously applied delivery must
    # participate in baseline diffs so overlays and binary patches can remove them.
    # The current working tree is still protected by the pre-GA hygiene gate, which
    # permits only the release artifacts belonging to the canonical current version.
    for pattern in ('HYDRA_V*_RELEASE_NOTES.md', 'VALIDAR_HYDRA_V*_WINDOWS.cmd'):
        for path in root.glob(pattern):
            if path.is_file() and not path.is_symlink():
                files.append(path)
    return sorted(set(files), key=lambda path: path.relative_to(root).as_posix())


def source_inventory(root: Path, version: str) -> dict[str, Path]:
    return {path.relative_to(root).as_posix(): path for path in source_files(root, version)}
