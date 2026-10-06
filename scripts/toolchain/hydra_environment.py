from __future__ import annotations

import hashlib
import json
import os
import platform
import re
import shutil
import socket
import subprocess
import sys
import tempfile
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable

ROOT = Path(__file__).resolve().parents[2]
DISCOVERY_POLICY_PATH = ROOT / "config/toolchain-discovery-policy.json"
TOOLCHAIN_PATH = ROOT / "config/hydra-toolchain.json"


def _json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def sha256_file(path: Path | None) -> str | None:
    if path is None or not path.is_file():
        return None
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _run(argv: list[str], *, cwd: Path = ROOT, timeout: int = 20) -> tuple[int, str]:
    try:
        completed = subprocess.run(argv, cwd=cwd, text=True, capture_output=True, timeout=timeout)
        return completed.returncode, (completed.stdout + "\n" + completed.stderr).strip()
    except Exception as exc:
        return 127, str(exc)


def command_argv(executable: Path, *args: str) -> list[str]:
    if os.name == "nt" and executable.suffix.lower() in {".cmd", ".bat"}:
        comspec = os.environ.get("COMSPEC") or shutil.which("cmd.exe") or "cmd.exe"
        return [comspec, "/d", "/s", "/c", subprocess.list2cmdline([str(executable), *args])]
    return [str(executable), *args]


def resolve_path_program(*names: str) -> Path | None:
    for name in names:
        found = shutil.which(name)
        if found:
            try:
                return Path(found).resolve()
            except OSError:
                return Path(found)
    return None


def _version(text: str) -> str:
    match = re.search(r"(\d+(?:\.\d+){0,3})", text)
    return match.group(1) if match else ""


def version_parts(value: str) -> tuple[int, ...]:
    try:
        return tuple(int(item) for item in value.split(".")) if value else ()
    except ValueError:
        return ()


def compatible_java_feature(version: str, allowed: Iterable[int]) -> bool:
    parts = version_parts(version)
    return bool(parts and parts[0] in set(int(item) for item in allowed))


def _expand_windows_path(value: str) -> Path:
    expanded = value
    for match in re.findall(r"%([^%]+)%", value):
        expanded = expanded.replace(f"%{match}%", os.environ.get(match, ""))
    return Path(expanded)


@dataclass(frozen=True)
class JdkCandidate:
    root: str
    source: str
    java: str | None
    javac: str | None
    version: str
    feature: int | None
    javaHomeProperty: str | None
    vendor: str | None
    accepted: bool
    reasons: tuple[str, ...]


def _java_properties(java: Path) -> tuple[str | None, str | None, str]:
    code, output = _run(command_argv(java, "-XshowSettings:properties", "-version"), timeout=20)
    java_home = None
    vendor = None
    for line in output.splitlines():
        stripped = line.strip()
        if stripped.startswith("java.home ="):
            java_home = stripped.split("=", 1)[1].strip()
        elif stripped.startswith("java.vendor ="):
            vendor = stripped.split("=", 1)[1].strip()
    version_line = next((line for line in output.splitlines() if re.search(r'(?i)\b(?:openjdk|java)\s+version\b', line)), '')
    if not version_line:
        version_line = next((line for line in output.splitlines() if line.strip().startswith('java.version =')), '')
    return java_home, vendor, _version(version_line)


def inspect_jdk(root: Path, source: str, allowed_features: Iterable[int], require_compiler: bool = True) -> JdkCandidate:
    try:
        root = root.expanduser().resolve()
    except OSError:
        root = root.expanduser()
    java = root / "bin" / ("java.exe" if os.name == "nt" else "java")
    javac = root / "bin" / ("javac.exe" if os.name == "nt" else "javac")
    reasons: list[str] = []
    version = ""
    java_home = None
    vendor = None
    if not java.is_file():
        reasons.append("missing-java")
    else:
        java_home, vendor, version = _java_properties(java)
        if not compatible_java_feature(version, allowed_features):
            reasons.append("unsupported-java-feature")
    if require_compiler and not javac.is_file():
        reasons.append("missing-javac")
    feature = version_parts(version)[0] if version_parts(version) else None
    return JdkCandidate(
        root=str(root), source=source,
        java=str(java) if java.is_file() else None,
        javac=str(javac) if javac.is_file() else None,
        version=version, feature=feature,
        javaHomeProperty=java_home, vendor=vendor,
        accepted=not reasons, reasons=tuple(reasons),
    )


def normalize_jdk_home(value: str | Path) -> Path:
    """Accept a JDK root, its ``bin`` directory or a java/javac executable."""
    raw = str(value).strip().strip('"')
    candidate = Path(raw).expanduser()
    name = candidate.name.lower()
    if name in {"java", "java.exe", "javac", "javac.exe"} and candidate.parent.name.lower() == "bin":
        return candidate.parent.parent
    if name == "bin":
        return candidate.parent
    return candidate


def _candidate_roots_from_java_executable(java_executable: Path) -> list[tuple[Path, str]]:
    roots: list[tuple[Path, str]] = []
    try:
        executable = java_executable.resolve()
    except OSError:
        executable = java_executable
    if executable.parent.name.lower() == "bin":
        roots.append((executable.parent.parent, "path"))
    java_home, _, _ = _java_properties(java_executable)
    if java_home:
        roots.append((Path(java_home), "java-home-property"))
    return roots


def _windows_registry_roots(keys: Iterable[str]) -> list[tuple[Path, str]]:
    if os.name != 'nt':
        return []
    try:
        import winreg  # type: ignore
    except ImportError:
        return []
    roots: list[tuple[Path, str]] = []

    def walk(key, label: str, depth: int) -> None:
        for value_name in ('JavaHome', 'Path', 'InstallationPath'):
            try:
                value, _ = winreg.QueryValueEx(key, value_name)
                if value:
                    roots.append((Path(str(value)), label))
            except OSError:
                pass
        if depth <= 0:
            return
        try:
            count = winreg.QueryInfoKey(key)[0]
        except OSError:
            return
        for index in range(count):
            try:
                sub_name = winreg.EnumKey(key, index)
                with winreg.OpenKey(key, sub_name) as sub:
                    walk(sub, label, depth - 1)
            except OSError:
                continue

    for hive in (winreg.HKEY_LOCAL_MACHINE, winreg.HKEY_CURRENT_USER):
        for view in (getattr(winreg, 'KEY_WOW64_64KEY', 0), getattr(winreg, 'KEY_WOW64_32KEY', 0)):
            for key_name in keys:
                try:
                    with winreg.OpenKey(hive, key_name, 0, winreg.KEY_READ | view) as key:
                        walk(key, f'windows-registry:{key_name}', 3)
                except OSError:
                    continue
    return roots


def discover_jdks(explicit: str | None = None, policy: dict | None = None) -> list[JdkCandidate]:
    policy = policy or _json(DISCOVERY_POLICY_PATH)
    java_policy = policy["java"]
    allowed = [int(item) for item in java_policy["qualificationFeatures"]]
    require_compiler = bool(java_policy.get("requireCompiler", True))
    roots: list[tuple[Path, str]] = []
    if explicit:
        roots.append((normalize_jdk_home(explicit), "explicit"))
    for env_name in java_policy.get("environmentVariables", []):
        value = os.environ.get(env_name)
        if value:
            roots.append((Path(value), f"environment:{env_name}"))
    project_depth = int(java_policy.get("maxProjectLocalDepth", 1))
    for raw in java_policy.get("projectLocalRoots", []):
        root = (ROOT / raw).expanduser()
        if not root.is_dir():
            continue
        roots.append((root, f"project-local:{raw}"))
        frontier = [(root, 0)]
        while frontier:
            current, depth = frontier.pop(0)
            if depth >= project_depth:
                continue
            try:
                children = [path for path in current.iterdir() if path.is_dir()]
            except OSError:
                continue
            for child in children:
                roots.append((child, f"project-local:{raw}"))
                frontier.append((child, depth + 1))
    java_path = resolve_path_program("java.exe" if os.name == "nt" else "java")
    if java_path:
        roots.extend(_candidate_roots_from_java_executable(java_path))
    javac_path = resolve_path_program("javac.exe" if os.name == "nt" else "javac")
    if javac_path and javac_path.parent.name.lower() == "bin":
        roots.append((javac_path.parent.parent, "path:javac"))
    roots.extend(_windows_registry_roots(java_policy.get("windowsRegistryKeys", [])))
    if os.name == "nt":
        max_depth = int(java_policy.get("maxFilesystemDepth", 2))
        for raw in java_policy.get("windowsRoots", []):
            root = _expand_windows_path(raw)
            if not root.is_dir():
                continue
            roots.append((root, f"filesystem:{raw}"))
            frontier = [(root, 0)]
            while frontier:
                current, depth = frontier.pop(0)
                if depth >= max_depth:
                    continue
                try:
                    children = [p for p in current.iterdir() if p.is_dir()]
                except OSError:
                    continue
                for child in children:
                    roots.append((child, f"filesystem:{raw}"))
                    frontier.append((child, depth + 1))
    dedup: dict[str, tuple[Path, str]] = {}
    for root, source in roots:
        try:
            key = str(root.expanduser().resolve()).casefold()
        except OSError:
            key = str(root).casefold()
        dedup.setdefault(key, (root, source))
    candidates = [inspect_jdk(root, source, allowed, require_compiler) for root, source in dedup.values()]
    order = list(policy.get('resolutionOrder', []))
    def source_rank(source: str) -> int:
        family = source.split(':', 1)[0]
        if source == 'java-home-property': family = 'java-home-property'
        try: return order.index(family)
        except ValueError: return len(order)
    candidates.sort(key=lambda item: (not item.accepted, source_rank(item.source), -(item.feature or -1), item.root.casefold()))
    return candidates


def select_jdk(explicit: str | None = None, policy: dict | None = None) -> JdkCandidate | None:
    return next((candidate for candidate in discover_jdks(explicit, policy) if candidate.accepted), None)


def _tool(path: Path | None, version_args: tuple[str, ...], *, version_parser=_version, cwd: Path = ROOT) -> dict:
    if path is None:
        return {"available": False, "path": None, "version": "", "sha256": None}
    code, output = _run(command_argv(path, *version_args), cwd=cwd)
    return {
        "available": code == 0,
        "path": str(path),
        "version": version_parser(output),
        "sha256": sha256_file(path) if path.is_file() and path.suffix.lower() not in {".cmd", ".bat"} else None,
        "output": output.splitlines()[0] if output else "",
    }


def collect_toolchain(explicit_jdk: str | None = None) -> dict:
    contract = _json(TOOLCHAIN_PATH)
    discovery = _json(DISCOVERY_POLICY_PATH)
    selected = select_jdk(explicit_jdk, discovery)
    node = ROOT / "frontend/node_modules/.bin/node"
    if os.name == "nt":
        local_node_candidates = [ROOT / "frontend/node_modules/.bin/node.exe"]
        local_npm_candidates = [ROOT / "frontend/node_modules/.bin/npm.cmd"]
        npm_path = next((p for p in local_npm_candidates if p.is_file()), None) or resolve_path_program("npm.cmd", "npm.exe", "npm")
        node_path = next((p for p in local_node_candidates if p.is_file()), None) or resolve_path_program("node.exe", "node")
    else:
        npm_path = resolve_path_program("npm")
        node_path = node if node.is_file() else resolve_path_program("node")
    maven_path = ROOT / ("mvnw.cmd" if os.name == "nt" else "mvnw")
    if not maven_path.is_file():
        maven_path = resolve_path_program("mvn")  # type: ignore[assignment]
    java_path = Path(selected.java) if selected and selected.java else None
    javac_path = Path(selected.javac) if selected and selected.javac else None
    java = _tool(java_path, ("-version",))
    javac = _tool(javac_path, ("-version",))
    node_info = _tool(node_path, ("--version",), version_parser=lambda text: _version(text))
    npm_info = _tool(npm_path, ("--version",), version_parser=lambda text: _version(text))
    # On Windows, probing npm.cmd through cmd.exe can fail on installations whose
    # path contains spaces even though Node/npm themselves are healthy. Prefer the
    # package CLI through the already-resolved Node executable as a deterministic
    # fallback; this is also the implementation ultimately invoked by npm.cmd.
    if os.name == "nt" and (not npm_info.get("available") or not npm_info.get("version")) and node_path is not None:
        npm_cli_candidates: list[Path] = []
        for base in (node_path.parent, npm_path.parent if npm_path is not None else None):
            if base is not None:
                npm_cli_candidates.append(base / "node_modules" / "npm" / "bin" / "npm-cli.js")
        seen_cli: set[str] = set()
        for npm_cli in npm_cli_candidates:
            key = str(npm_cli).casefold()
            if key in seen_cli or not npm_cli.is_file():
                continue
            seen_cli.add(key)
            code, output = _run([str(node_path), str(npm_cli), "--version"])
            if code == 0 and _version(output):
                npm_info = {
                    "available": True,
                    "path": str(npm_path) if npm_path is not None else str(npm_cli),
                    "version": _version(output),
                    "sha256": sha256_file(npm_cli),
                    "output": output.splitlines()[0] if output else "",
                    "probePath": str(npm_cli),
                }
                break
    if isinstance(maven_path, Path) and maven_path.is_file() and maven_path.name in {'mvnw','mvnw.cmd'}:
        wrapper_props = ROOT / '.mvn/wrapper/maven-wrapper.properties'
        wrapper_text = wrapper_props.read_text(encoding='utf-8') if wrapper_props.is_file() else ''
        match = re.search(r'apache-maven-([0-9.]+)-bin', wrapper_text)
        maven_info = {'available': True, 'path': str(maven_path), 'version': match.group(1) if match else '', 'sha256': sha256_file(maven_path), 'output': 'project Maven Wrapper'}
    else:
        maven_info = _tool(maven_path, ('-version',)) if isinstance(maven_path, Path) else {'available': False, 'path': None, 'version': '', 'sha256': None}
    ts_package = ROOT / "frontend/node_modules/typescript/package.json"
    typescript = {
        "available": ts_package.is_file(),
        "path": str(ts_package) if ts_package.is_file() else None,
        "version": json.loads(ts_package.read_text(encoding="utf-8"))["version"] if ts_package.is_file() else "",
        "sha256": sha256_file(ts_package),
    }
    playwright_package = ROOT / "frontend/node_modules/@playwright/test/package.json"
    playwright = {
        "available": playwright_package.is_file(),
        "path": str(playwright_package) if playwright_package.is_file() else None,
        "version": json.loads(playwright_package.read_text(encoding="utf-8"))["version"] if playwright_package.is_file() else "",
        "sha256": sha256_file(playwright_package),
    }
    allowed = discovery["java"]["qualificationFeatures"]
    host_compatible = bool(
        selected and selected.accepted and
        java["available"] and javac["available"] and
        node_info["available"] and npm_info["available"] and maven_info["available"]
    )
    full_compatible = bool(host_compatible and typescript["available"] and playwright["available"])
    return {
        "schemaVersion": "2.1",
        "compatible": host_compatible,
        "hostCompatible": host_compatible,
        "fullCompatible": full_compatible,
        "bytecodeTarget": int(discovery["java"]["bytecodeTarget"]),
        "javaQualificationFeatures": allowed,
        "jdk": asdict(selected) if selected else None,
        "tools": {"java": java, "javac": javac, "node": node_info, "npm": npm_info, "typescript": typescript, "maven": maven_info, "playwright": playwright},
        "contract": contract,
        "platform": {"system": platform.system(), "release": platform.release(), "machine": platform.machine(), "python": platform.python_version()},
    }


def port_available(port: int, host: str = "127.0.0.1") -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(0.25)
        return sock.connect_ex((host, port)) != 0


def write_probe(directory: Path) -> tuple[bool, str]:
    try:
        directory.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(prefix="hydra-probe-", dir=directory, delete=False) as handle:
            path = Path(handle.name)
            handle.write(b"hydra")
        path.unlink(missing_ok=True)
        return True, "writable"
    except Exception as exc:
        return False, str(exc)
