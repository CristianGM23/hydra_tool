#!/usr/bin/env python3
import hashlib
import json
import sys
from pathlib import Path


def fail(message: str, code: int = 70) -> None:
    print(message, file=sys.stderr)
    raise SystemExit(code)


def safe(workspace: Path, relative: str) -> Path:
    raw = (relative or '').strip().replace('\\', '/')
    if not raw or raw.startswith('/') or (len(raw) > 1 and raw[1] == ':'):
        fail('HYDRA native runtime received an invalid workspace-relative path', 64)
    target = (workspace / raw).resolve()
    if workspace != target and workspace not in target.parents:
        fail('HYDRA native runtime path escapes the isolated workspace', 64)
    return target


def load(path: Path):
    try:
        return json.loads(path.read_text(encoding='utf-8'))
    except Exception as exc:
        fail(f'HYDRA Python native runtime could not read JSON evidence: {exc}', 65)


def main() -> None:
    if len(sys.argv) != 2:
        fail('HYDRA Python native runtime expects one bridge request file', 64)
    request_path = Path(sys.argv[1]).resolve()
    workspace = request_path.parent
    request = load(request_path)
    source = load(safe(workspace, request.get('sourceModelFile', '')))
    plan = load(workspace / 'export' / 'native' / 'native-plan.json')
    direction = request.get('direction') or 'FORWARD'
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
    target_path.write_text(json.dumps(target_model, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    result_path = safe(workspace, request.get('resultFile', ''))
    result = {
        'schemaVersion': str(request.get('schemaVersion') or '1.0'),
        'transformationId': str(request.get('transformationId') or ''),
        'language': 'PYTHON',
        'kind': 'M2M',
        'direction': direction,
        'targetModelFile': str(target_path.relative_to(workspace)).replace('\\', '/'),
        'artifacts': [],
        'diagnostics': ['Executed by HYDRA Official Python Native Runtime'],
    }
    result_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
