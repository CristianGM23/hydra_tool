#!/usr/bin/env python3
"""Independent canonical MDE instance oracle for HYDRA reference projects.

This verifies the input model, NOT functional parity of an exported runtime.
A native certification must independently test the exported project and compare
outputs to this oracle; it must not substitute this oracle for native execution.
"""
from __future__ import annotations
import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path
from typing import Any

NUMERIC = {'INTEGER', 'INT', 'LONG', 'SHORT', 'DOUBLE', 'FLOAT', 'DECIMAL', 'NUMBER'}
TEXT = {'STRING', 'CHAR', 'CHARACTER'}
BOOL = {'BOOLEAN', 'BOOL'}


def validate(metamodel: dict, model: dict) -> dict:
    issues: list[dict] = []
    def issue(code: str, at: str, detail: str):
        issues.append({'code': code, 'at': at, 'detail': detail})
    classes = {c['id']: c for c in metamodel.get('classes', [])}
    attributes = {c['id']: {a['id']: a for a in c.get('attributes', [])} for c in classes.values()}
    relations = {r['id']: r for r in metamodel.get('relations', [])}
    elements = {}
    for element in model.get('elements', []):
        eid = element['id']
        if eid in elements: issue('DUPLICATE_ELEMENT', eid, 'Duplicate instance identity')
        elements[eid] = element
        if element.get('classId') not in classes:
            issue('UNKNOWN_CLASS', eid, 'Class not defined in metamodel')
    inheritance = defaultdict(set)
    for relation in metamodel.get('relations', []):
        if relation.get('kind') in ('INHERITANCE', 'IMPLEMENTATION'):
            inheritance[relation['sourceTypeId']].add(relation['targetTypeId'])
    def conforms(actual: str, expected: str, trail=()) -> bool:
        if actual == expected: return True
        if actual in trail: return False
        return any(conforms(p, expected, (*trail, actual)) for p in inheritance.get(actual, ()))
    for eid, element in elements.items():
        cid = element.get('classId')
        if cid not in classes: continue
        visible = {}
        to_visit = [cid]
        while to_visit:
            parent = to_visit.pop()
            if parent not in classes or parent in visible: continue
            visible[parent] = True
            to_visit.extend(inheritance.get(parent, []))
        allowed = {a: spec for c in visible for a, spec in attributes[c].items()}
        values = element.get('attributes', {})
        if not isinstance(values, dict):
            issue('INVALID_ATTRIBUTES', eid, 'Attribute value map must be object');continue
        for aid in values.keys() - allowed.keys():
            issue('UNKNOWN_ATTRIBUTE', eid, aid)
        for aid, spec in allowed.items():
            value = values.get(aid)
            mul = spec.get('multiplicity') or {}
            low = mul.get('lowerBound', 0)
            high = mul.get('upperBound')
            count = 0 if value is None else len(value) if isinstance(value, list) else 1
            if count < low or (high is not None and count > high):
                issue('ATTRIBUTE_CARDINALITY', eid, aid)
            if value is None: continue
            for item in value if isinstance(value, list) else [value]:
                type_id = (spec.get('type') or {}).get('targetId', '')
                if (type_id in TEXT and not isinstance(item, str)) or \
                   (type_id in BOOL and not isinstance(item, bool)) or \
                   (type_id in NUMERIC and (isinstance(item, bool) or not isinstance(item, (int, float)))):
                    issue('ATTRIBUTE_TYPE', eid, aid)
            if spec.get('unique') and isinstance(value, list):
                try:
                    if len({json.dumps(v, sort_keys=True) for v in value}) != len(value):
                        issue('ATTRIBUTE_NOT_UNIQUE', eid, aid)
                except (TypeError, ValueError): issue('ATTRIBUTE_TYPE', eid, aid)
    incoming = Counter()
    edges = defaultdict(list)
    relation_counts = Counter()
    containment_parents = defaultdict(set)
    for edge in model.get('relations', []):
        rid = edge.get('relationId', '')
        source = edge.get('sourceElementId', '')
        target = edge.get('targetElementId', '')
        if rid not in relations:
            issue('UNKNOWN_RELATION', edge.get('id', ''), rid);continue
        if source not in elements or target not in elements:
            issue('DANGLING_REFERENCE', edge.get('id', ''), rid);continue
        rel = relations[rid]
        if not conforms(elements[source]['classId'], rel['sourceTypeId']):
            issue('RELATION_SOURCE_TYPE', edge.get('id', ''), rid)
        if not conforms(elements[target]['classId'], rel['targetTypeId']):
            issue('RELATION_TARGET_TYPE', edge.get('id', ''), rid)
        relation_counts[(rid, source)] += 1
        incoming[(rid, target)] += 1
        edges[(rid, source)].append(target)
        if rel.get('kind') == 'CONTAINMENT': containment_parents[target].add(source)
    for rel in relations.values():
        if rel.get('kind') in ('INHERITANCE', 'IMPLEMENTATION'):continue
        rid = rel['id']; mul = rel.get('targetMultiplicity') or {}
        lower = mul.get('lowerBound', 0); upper = mul.get('upperBound')
        for eid, element in elements.items():
            if not conforms(element.get('classId', ''), rel['sourceTypeId']): continue
            count = relation_counts[(rid, eid)]
            if count < lower or (upper is not None and count > upper): issue('REFERENCE_CARDINALITY', eid, rid)
            if rel.get('unique') and len(set(edges[(rid,eid)])) != count: issue('REFERENCE_NOT_UNIQUE', eid, rid)
    for target, parents in containment_parents.items():
        if len(parents)>1:issue('MULTIPLE_CONTAINERS', target, ','.join(sorted(parents)))
    roots = model.get('rootElementIds', [])
    for eid in roots:
        if eid not in elements:issue('UNKNOWN_ROOT', eid, 'Missing root element')
    for node in elements:
        current = node; history = set()
        while current in containment_parents and containment_parents[current]:
            parent = sorted(containment_parents[current])[0]
            if parent in history or parent == node:
                issue('CONTAINMENT_CYCLE', node, 'Containment cycle detected');break
            history.add(parent);current=parent
    return {'schemaVersion':1,'status':'VALID' if not issues else 'INVALID',
            'metamodelId':metamodel.get('id'),'modelId':model.get('id'),
            'elements':len(model.get('elements',[])), 'links':len(model.get('relations',[])),
            'issues':sorted(issues,key=lambda i:(i['code'],i['at'],i['detail'])),
            'nativeExecutionCertified':False,'exportedArtifactTested':False}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--metamodel',type=Path,required=True);p.add_argument('--model',type=Path,required=True)
    p.add_argument('--report',type=Path)
    a=p.parse_args()
    result=validate(json.loads(a.metamodel.read_text(encoding='utf-8')),
                    json.loads(a.model.read_text(encoding='utf-8')))
    payload=json.dumps(result,sort_keys=True,indent=2)+'\n'
    if a.report:a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(payload,encoding='utf-8')
    print(payload,end='')
    return 0 if result['status']=='VALID' else 1

if __name__=='__main__':raise SystemExit(main())
