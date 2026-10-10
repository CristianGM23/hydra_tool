#!/usr/bin/env python3
"""B8/B9 canonical model depth and fail-closed export provenance regression.

No synthetic ZIP used in these tests is counted as an exported HYDRA project or
as proof of real EMF/Langium toolchain parity.
"""
from __future__ import annotations
import copy
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
from zipfile import ZipFile,ZIP_DEFLATED
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/validation'))
from fix180_model_oracle import validate
from fix180_export_provenance import bind_sources, fingerprint, read_export, verify
FIXTURE=ROOT/'reference-projects/fix180-advanced-canonical/artifacts'

class CanonicalModelSemantics(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.meta=json.loads((FIXTURE/'course.metamodel.json').read_text(encoding='utf-8'))
        cls.model=json.loads((FIXTURE/'course.model.json').read_text(encoding='utf-8'))
    def verdict(self,mutation):
        model=copy.deepcopy(self.model);mutation(model)
        return validate(self.meta,model)
    def assert_issue(self,code,mutation):
        result=self.verdict(mutation)
        self.assertEqual(result['status'],'INVALID')
        self.assertIn(code,{item['code'] for item in result['issues']})
        self.assertFalse(result['nativeExecutionCertified'])
    def test_original_reference_project_valid(self):
        result=validate(self.meta,self.model)
        self.assertEqual(result['status'],'VALID',result)
        self.assertEqual(result['elements'],5)
        self.assertEqual(result['links'],4)
    def test_dangling_reference(self):
        self.assert_issue('DANGLING_REFERENCE',lambda m:m['relations'][0].update(targetElementId='unknown'))
    def test_wrong_reference_target_class(self):
        self.assert_issue('RELATION_TARGET_TYPE',lambda m:m['relations'][0].update(targetElementId='object:alice'))
    def test_relation_missing_required_cardinality(self):
        self.assert_issue('REFERENCE_CARDINALITY',lambda m:(m['relations'].pop(0),m['relations'].pop(0)))
    def test_relation_exceeds_upper_cardinality(self):
        def mutate(m):m['relations'] += [copy.deepcopy(m['relations'][0]),copy.deepcopy(m['relations'][0])]
        self.assert_issue('REFERENCE_CARDINALITY',mutate)
    def test_relation_is_unique(self):
        self.assert_issue('REFERENCE_NOT_UNIQUE',lambda m:m['relations'].append(copy.deepcopy(m['relations'][0])))
    def test_no_double_containment(self):
        def mutate(m):
            x=copy.deepcopy(m['relations'][0]);x['sourceElementId']='object:other-course';m['elements'].append({'id':'object:other-course','classId':'class:Course','attributes':{'attr:course:title':'Other'}});m['relations'].append(x)
        self.assert_issue('MULTIPLE_CONTAINERS',mutate)
    def test_containment_cycle(self):
        meta=copy.deepcopy(self.meta)
        meta['relations'].append({'id':'rel:cycle','sourceTypeId':'class:Lesson','targetTypeId':'class:Course','kind':'CONTAINMENT','targetMultiplicity':{'lowerBound':0,'upperBound':1}})
        instance=copy.deepcopy(self.model)
        instance['relations'].append({'id':'edge:cycle','relationId':'rel:cycle','sourceElementId':'object:lesson-one','targetElementId':'object:course'})
        self.assertIn('CONTAINMENT_CYCLE',{issue['code'] for issue in validate(meta,instance)['issues']})
    def test_unknown_element_class(self):self.assert_issue('UNKNOWN_CLASS',lambda m:m['elements'][0].update(classId='missing'))
    def test_required_attribute_removed(self):self.assert_issue('ATTRIBUTE_CARDINALITY',lambda m:m['elements'][0]['attributes'].clear())
    def test_inherited_attribute_is_recognized(self):
        self.assertEqual(self.model['elements'][2]['classId'],'class:VideoLesson')
        self.assertEqual(validate(self.meta,self.model)['status'],'VALID')
    def test_wrong_inherited_attribute_type(self):
        self.assert_issue('ATTRIBUTE_TYPE',lambda m:m['elements'][2]['attributes'].update({'attr:lesson:duration':'oops'}))
    def test_illegal_attribute_id(self):self.assert_issue('UNKNOWN_ATTRIBUTE',lambda m:m['elements'][0]['attributes'].update({'invalid':5}))
    def test_duplicate_element_id(self):self.assert_issue('DUPLICATE_ELEMENT',lambda m:m['elements'].append(copy.deepcopy(m['elements'][0])))
    def test_unknown_root(self):self.assert_issue('UNKNOWN_ROOT',lambda m:m['rootElementIds'].append('none'))
    def test_unique_multivalue_attribute(self):
        self.assert_issue('ATTRIBUTE_NOT_UNIQUE',lambda m:m['elements'][1]['attributes'].update({'attr:lesson:tags':['mde','mde']}))
    def test_invalid_boolean(self):self.assert_issue('ATTRIBUTE_TYPE',lambda m:m['elements'][2]['attributes'].update({'attr:video:published':'true'}))

class ExportEvidenceContract(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)
        self.source=b'{"schemaVersion":"2.1","id":"course"}'
        (self.root/'model.json').write_bytes(self.source)
        self.snapshot={'schemaVersion':'1.0','metamodelId':'course','metamodelFingerprint':'abc123',
            'projectExport':'langium','selectionMode':'ALL','editorId':'',
            'settings':{},'artifacts':[{'kind':'METAMODEL','documentId':'course','bytes':len(self.source),'sha256':hashlib.sha256(self.source).hexdigest()}]}
        self.snapshot['fingerprint']=fingerprint(self.snapshot)
        self.inventory=self.root/'sources.json'
        self.inventory.write_text(json.dumps({'metamodelId':'course','artifacts':[{'kind':'METAMODEL','documentId':'course','relativePath':'model.json'}]}))
    def make_zip(self,override=None):
        snapshot=copy.deepcopy(self.snapshot)
        evidence={'schemaVersion':'1.1','metamodelId':'course','projectExport':'langium','generatedProjectStructuralValidation':'PASSED','generationSnapshotFingerprint':snapshot['fingerprint']}
        contents={'META-INF/hydra-project-generation-snapshot.json':json.dumps(snapshot).encode(),
                  'META-INF/hydra-metamodel-generation-gate.json':json.dumps(evidence).encode(),
                  'package.json':b'{"name":"test","scripts":{"test":"false"}}',
                  'language.langium':b'grammar Course\nentry Model: name=ID;'}
        if override:override(contents,snapshot,evidence)
        contents['META-INF/hydra-project-generation-snapshot.json']=json.dumps(snapshot).encode()
        contents['META-INF/hydra-metamodel-generation-gate.json']=json.dumps(evidence).encode()
        manifest={'technology':'LANGIUM','schemaVersion':'1.0','files':[
            {'path':name,'mediaType':'application/octet-stream','sha256':hashlib.sha256(content).hexdigest()}
            for name,content in sorted(contents.items())]}
        contents['META-INF/hydra-technology-compilation.json']=json.dumps(manifest).encode()
        zip_path=self.root/'export.zip'
        with ZipFile(zip_path,'w',ZIP_DEFLATED) as zipfile:
            for name,body in contents.items():zipfile.writestr(name,body)
        return zip_path
    def test_provenance_proof_is_not_parity(self):
        report=verify(self.make_zip(),self.inventory)
        self.assertEqual(report['sourceBinding']['status'],'BOUND')
        self.assertFalse(report['functionalParityCertified'])
        self.assertFalse(report['nativeBuildExecuted'])
    def test_without_sources_is_unbound(self):
        self.assertEqual(verify(self.make_zip())['sourceBinding']['status'],'UNBOUND')
    def test_native_requires_trusted_optin(self):
        with self.assertRaisesRegex(ValueError,'trusted'):
            verify(self.make_zip(),self.inventory,native_profile='langium',execute=True)
    def test_native_requires_sources(self):
        with self.assertRaisesRegex(ValueError,'bound'):
            verify(self.make_zip(),native_profile='langium',execute=True,trusted_project=True)
    def test_source_content_tampered(self):
        pkg=self.make_zip();(self.root/'model.json').write_text('different')
        with self.assertRaisesRegex(ValueError,'diverge'):verify(pkg,self.inventory)
    def test_source_outside_inventory(self):
        self.inventory.write_text(json.dumps({'metamodelId':'course','artifacts':[{'kind':'METAMODEL','documentId':'course','relativePath':'../secret'}]}))
        with self.assertRaises((ValueError,OSError)):verify(self.make_zip(),self.inventory)
    def test_source_inconsistent_model_id(self):
        data=json.loads(self.inventory.read_text());data['metamodelId']='other';self.inventory.write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError,'metamodel'):verify(self.make_zip(),self.inventory)
    def test_snapshot_fingerprint_corrupted(self):
        def change(c,s,e):s['fingerprint']='0'*64;e['generationSnapshotFingerprint']=s['fingerprint']
        with self.assertRaisesRegex(ValueError,'fingerprint'):verify(self.make_zip(change),self.inventory)
    def test_gate_and_snapshot_disagree(self):
        with self.assertRaisesRegex(ValueError,'disagree'):verify(self.make_zip(lambda c,s,e:e.update(projectExport='emf-eclipse')),self.inventory)
    def test_structural_failure_blocks(self):
        with self.assertRaisesRegex(ValueError,'structural'):verify(self.make_zip(lambda c,s,e:e.update(generatedProjectStructuralValidation='FAILED')),self.inventory)
    def test_unknown_provenance_still_not_native(self):
        self.assertFalse(verify(self.make_zip(),self.inventory)['functionalParityCertified'])
    def test_rejects_unexpected_zip_path(self):
        pkg=self.make_zip()
        with ZipFile(pkg,'a') as zipfile:zipfile.writestr('../malware','bad')
        with self.assertRaisesRegex(ValueError,'Unsafe'):verify(pkg,self.inventory)
    def test_rejects_undeclared_file(self):
        pkg=self.make_zip()
        with ZipFile(pkg,'a') as zipfile:zipfile.writestr('evil.js','bad')
        with self.assertRaisesRegex(ValueError,'inventory'):verify(pkg,self.inventory)
    def test_rejects_portable_case_collision(self):
        pkg=self.make_zip()
        with ZipFile(pkg,'a') as zipfile:zipfile.writestr('PACKAGE.JSON','bad')
        with self.assertRaisesRegex(ValueError,'colliding'):verify(pkg,self.inventory)
    def test_rejects_modified_member_digest(self):
        pkg=self.make_zip()
        with ZipFile(pkg) as archive:parts={i.filename:archive.read(i) for i in archive.infolist()}
        parts['language.langium']=b'not original'
        with ZipFile(pkg,'w') as archive:
            for name,body in parts.items():archive.writestr(name,body)
        with self.assertRaisesRegex(ValueError,'modified'):verify(pkg,self.inventory)

if __name__=='__main__':unittest.main(verbosity=2)
