#!/usr/bin/env python3
"""D8/D9 real filesystem migration transaction, interruption and rollback tests."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import sys
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/distribution'))
from migrate_standalone_models import migrate,recover,rollback_data,journal_dir

class ActualStandaloneModelMigrations(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);self.data=self.root/'user-data';self.data.mkdir()
        self.plan=self.root/'plan.json'
        self.plan.write_text(json.dumps({'schemaVersion':1,'fromVersion':'1.0','toVersion':'1.1',
            'metamodelId':'library','renameAttributes':{'attribute:class:Book:pages':'attribute:class:Book:pageCount'}}))
        self.files=[];self.old=[]
        for i in range(3):
            file=self.data/f'library-{i}.model.json'
            file.write_text(json.dumps({'schemaVersion':'1.0','id':f'library-{i}','metamodelId':'library',
                'elements':[{'id':str(i),'attributes':{'attribute:class:Book:pages':i+1}}],
                'relations':[]}),encoding='utf-8')
            self.files.append(file);self.old.append(file.read_bytes())
        (self.data/'notes.txt').write_text('Private work notes')
    def test_migration_changes_models_only(self):
        report=migrate(self.data,self.plan)
        self.assertEqual(report['files'],3)
        for file in self.files:
            document=json.loads(file.read_text())
            self.assertEqual(document['schemaVersion'],'1.1')
            self.assertIn('attribute:class:Book:pageCount',document['elements'][0]['attributes'])
        self.assertEqual((self.data/'notes.txt').read_text(),'Private work notes')
        self.assertFalse(report['nativeInstallerCertified'])
    def test_rollback_restores_original_bytes(self):
        migrate(self.data,self.plan)
        self.assertEqual(rollback_data(self.data)['status'],'ROLLED_BACK')
        self.assertEqual([f.read_bytes() for f in self.files],self.old)
    def test_interrupted_migration_rolls_back_without_losing_bytes(self):
        with self.assertRaisesRegex(RuntimeError,'INJECTED'):
            migrate(self.data,self.plan,simulate_failure_after=2)
        self.assertEqual([f.read_bytes() for f in self.files],self.old)
        self.assertEqual(json.loads((journal_dir(self.data)/'journal.json').read_text())['state'],'RECOVERED')
    def test_migration_rejects_mixed_source_versions(self):
        obj=json.loads(self.files[1].read_text());obj['schemaVersion']='1.2';self.files[1].write_text(json.dumps(obj))
        before=[p.read_bytes() for p in self.files]
        with self.assertRaisesRegex(ValueError,'schema'):migrate(self.data,self.plan)
        self.assertEqual([p.read_bytes() for p in self.files],before)
    def test_changed_migrated_model_blocks_rollback(self):
        migrate(self.data,self.plan)
        content=json.loads(self.files[0].read_text());content['name']='new user edit';self.files[0].write_text(json.dumps(content))
        with self.assertRaisesRegex(ValueError,'user modified'):rollback_data(self.data)
    def test_second_migration_requires_explicit_journal_handling(self):
        migrate(self.data,self.plan)
        with self.assertRaisesRegex(ValueError,'journal exists'):migrate(self.data,self.plan)
    def test_unknown_mapping_target_conflict_blocks_migration(self):
        document=json.loads(self.files[0].read_text())
        document['elements'][0]['attributes']['attribute:class:Book:pageCount']=99
        self.files[0].write_text(json.dumps(document))
        with self.assertRaisesRegex(ValueError,'conflict'):migrate(self.data,self.plan)
    def test_duplicate_mapping_targets_blocked(self):
        obj=json.loads(self.plan.read_text());obj['renameAttributes']['other']='attribute:class:Book:pageCount'
        self.plan.write_text(json.dumps(obj))
        with self.assertRaisesRegex(ValueError,'Two attributes'):migrate(self.data,self.plan)
    def test_invalid_plan_same_version_blocked(self):
        obj=json.loads(self.plan.read_text());obj['toVersion']=obj['fromVersion']
        self.plan.write_text(json.dumps(obj))
        with self.assertRaisesRegex(ValueError,'No-op'):migrate(self.data,self.plan)
    def test_invalid_plan_metamodel_blocked(self):
        obj=json.loads(self.plan.read_text());obj['metamodelId']='wrong'
        self.plan.write_text(json.dumps(obj))
        with self.assertRaisesRegex(ValueError,'schema/metamodel'):migrate(self.data,self.plan)
    def test_symlink_model_blocks_migration(self):
        try:(self.data/'alien.model.json').symlink_to(self.plan)
        except OSError:self.skipTest('symbolic links unsupported')
        with self.assertRaisesRegex(ValueError,'Symbolic'):migrate(self.data,self.plan)
    def test_journal_backup_tamper_blocks_rollback(self):
        migrate(self.data,self.plan)
        (journal_dir(self.data)/'backup'/self.files[0].name).write_text('bad')
        with self.assertRaisesRegex(ValueError,'backup was modified'):rollback_data(self.data)
    def test_data_root_symlink_forbidden(self):
        link=self.root/'linked'
        try:link.symlink_to(self.data,target_is_directory=True)
        except OSError:self.skipTest('symbolic links unsupported')
        with self.assertRaisesRegex(ValueError,'symbolic'):migrate(link,self.plan)
    def test_no_models_cannot_migrate(self):
        for f in self.files:f.unlink()
        with self.assertRaisesRegex(ValueError,'No models'):migrate(self.data,self.plan)
    def test_migration_has_verifiable_backup_checksums(self):
        migrate(self.data,self.plan)
        journal=json.loads((journal_dir(self.data)/'journal.json').read_text())
        self.assertEqual(journal['state'],'COMMITTED')
        for entry,original in zip(journal['files'],self.old):
            self.assertEqual(entry['before'],hashlib.sha256(original).hexdigest())

if __name__=='__main__':unittest.main(verbosity=2)
