#!/usr/bin/env python3
import hashlib
import json
import re
import sys
from pathlib import Path

TOKEN = re.compile(r'\{\{\s*(id|classId|attr:[A-Za-z0-9][A-Za-z0-9._:-]{0,239})\s*}}', re.MULTILINE)


def fail(message, code=65):
    print(message, file=sys.stderr)
    raise SystemExit(code)


def safe(workspace: Path, relative: str) -> Path:
    raw = str(relative or '').strip().replace('\\', '/')
    if not raw or raw.startswith('/') or (len(raw) > 1 and raw[1] == ':'):
        fail('HYDRA Python native runtime received an invalid workspace-relative path', 64)
    target = (workspace / raw).resolve()
    try:
        target.relative_to(workspace.resolve())
    except ValueError:
        fail('HYDRA Python native runtime path escapes the isolated workspace', 64)
    return target


def load(path: Path):
    try:
        return json.loads(path.read_text(encoding='utf-8'))
    except Exception:
        fail('HYDRA Python native runtime could not read JSON evidence', 65)


def write_json(path: Path, value):
    try:
        path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    except Exception:
        fail('HYDRA Python native runtime could not persist evidence', 70)


def stringify(value):
    if value is None:
        return ''
    if isinstance(value, bool):
        return 'true' if value else 'false'
    return str(value)


def render(template: str, element: dict) -> str:
    attrs = element.get('attributes') or {}
    def replacement(match):
        token = match.group(1)
        if token == 'id':
            value = element.get('id')
        elif token == 'classId':
            value = element.get('classId')
        else:
            value = attrs.get(token[len('attr:'):])
        return stringify(value)
    return TOKEN.sub(replacement, template or '')


def validate_logical_path(path: str):
    normalized = str(path or '').strip().replace('\\', '/')
    if not normalized or normalized.startswith('/') or '../' in normalized or normalized == '..' or (len(normalized) > 1 and normalized[1] == ':'):
        fail('Generated artifact path must be relative and portable: ' + str(path), 78)
    return normalized


def execute_m2t(workspace: Path, request: dict, source: dict, plan: dict, direction: str):
    if direction != 'FORWARD':
        fail('HYDRA Python native M2T runtime supports FORWARD only', 78)
    artifacts = {}
    for element in source.get('elements') or []:
        class_id = str(element.get('classId') or '')
        for rule in plan.get('generationRules') or []:
            if class_id != str(rule.get('contextTypeId') or ''):
                continue
            logical = validate_logical_path(render(str(rule.get('outputPathTemplate') or ''), element))
            content = render(str(rule.get('contentTemplate') or ''), element)
            if logical in artifacts and str(rule.get('overwritePolicy') or 'FAIL') == 'FAIL':
                fail('Generated artifact collision: ' + logical, 78)
            artifacts[logical] = content

    output_dir = safe(workspace, request.get('outputDirectory', ''))
    output_dir.mkdir(parents=True, exist_ok=True)
    evidence = []
    for index, (logical, content) in enumerate(artifacts.items(), start=1):
        file_path = output_dir / f'artifact-{index:06d}.txt'
        file_path.write_text(content, encoding='utf-8')
        evidence.append({'path': logical, 'file': str(file_path.relative_to(workspace)).replace('\\', '/')})

    result = {
        'schemaVersion': str(request.get('schemaVersion') or '1.0'),
        'transformationId': str(request.get('transformationId') or ''),
        'language': 'PYTHON',
        'kind': 'M2T',
        'direction': direction,
        'targetModelFile': '',
        'artifacts': evidence,
        'diagnostics': ['Executed by HYDRA Official Python Native M2T Runtime'],
    }
    write_json(safe(workspace, request.get('resultFile', '')), result)


def execute_m2m(workspace: Path, request: dict, source: dict, plan: dict, direction: str):
    roots = set(source.get('rootElementIds') or [])
    elements = []
    target_roots = []
    counter = 0
    for source_element in source.get('elements') or []:
        source_id = str(source_element.get('id') or '')
        class_id = str(source_element.get('classId') or '')
        attributes = source_element.get('attributes') or {}
        for rule in plan.get('rules') or []:
            if class_id not in (rule.get('sourceTypeIds') or []):
                continue
            counter += 1
            target_id = f'native-{counter}'
            target_attributes = {}
            for binding in rule.get('bindings') or []:
                if binding.get('kind') == 'DIRECT':
                    target_attributes[str(binding.get('targetFeatureId') or '')] = attributes.get(str(binding.get('sourceFeatureId') or ''))
                elif binding.get('kind') == 'CONSTANT':
                    target_attributes[str(binding.get('targetFeatureId') or '')] = binding.get('literalValue')
                else:
                    fail('HYDRA Python native runtime received an unsupported binding kind', 78)
            elements.append({
                'id': target_id,
                'classId': str(rule.get('targetTypeId') or ''),
                'attributes': target_attributes,
                'annotations': {},
            })
            if source_id in roots:
                target_roots.append(target_id)
    seed = f"{plan.get('transformationId','')}|{direction}|{source.get('id','')}".encode('utf-8')
    model_id = 'native-model-' + hashlib.sha256(seed).hexdigest()[:32]
    target_model = {
        'schemaVersion': '1.0',
        'id': model_id,
        'metamodelId': str(plan.get('targetMetamodelId') or ''),
        'metamodelEtag': str(plan.get('targetMetamodelFingerprint') or ''),
        'name': f"{source.get('name','')} native {plan.get('transformationName','')}",
        'rootElementIds': target_roots,
        'elements': elements,
        'relations': [],
        'annotations': {
            'hydra.native.runtime': 'PYTHON',
            'hydra.transformation.direction': direction,
        },
    }
    output_dir = safe(workspace, request.get('outputDirectory', ''))
    output_dir.mkdir(parents=True, exist_ok=True)
    target_path = output_dir / 'target-model.json'
    write_json(target_path, target_model)
    result = {
        'schemaVersion': str(request.get('schemaVersion') or '1.0'),
        'transformationId': str(request.get('transformationId') or ''),
        'language': 'PYTHON',
        'kind': 'M2M',
        'direction': direction,
        'targetModelFile': str(target_path.relative_to(workspace)).replace('\\', '/'),
        'artifacts': [],
        'diagnostics': ['Executed by HYDRA Official Python Native M2M Runtime'],
    }
    write_json(safe(workspace, request.get('resultFile', '')), result)


def main():
    if len(sys.argv) != 2:
        fail('HYDRA Python native runtime expects one bridge request file', 64)
    request_path = Path(sys.argv[1]).resolve()
    workspace = request_path.parent
    request = load(request_path)
    direction = str(request.get('direction') or 'FORWARD')
    source = load(safe(workspace, request.get('sourceModelFile', '')))
    plan = load(workspace / 'export' / 'native' / 'native-plan.json')
    kind = str(request.get('kind') or plan.get('kind') or 'M2M')
    if kind == 'M2T':
        execute_m2t(workspace, request, source, plan, direction)
    elif kind == 'M2M':
        execute_m2m(workspace, request, source, plan, direction)
    else:
        fail('HYDRA Python native runtime received an unsupported transformation kind', 78)


if __name__ == '__main__':
    main()
