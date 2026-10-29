#!/usr/bin/env python3
"""Fix184 repeatable security and model-persistence regression tests; no native claims."""
from __future__ import annotations
import base64
import importlib.util
import json
import os
import re
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
path = ROOT/'scripts/distribution/configure_generated_tauri_updater.py'
spec = importlib.util.spec_from_file_location('hydra_updater', path)
updater = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = updater
spec.loader.exec_module(updater)
PUBKEY = base64.b64encode(b'untrusted comment: minisign public key: test\n' + base64.b64encode(b'Ed' + b'K'*8 + b'P'*32) + b'\n').decode()
URL = 'https://releases.example.org/{{target}}/{{arch}}/{{current_version}}'

class UpdaterRegression(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='hydra-fix184-')
        self.addCleanup(self.temp.cleanup)
        self.editor = Path(self.temp.name)/'generated-editor'
        (self.editor/'src-tauri/src').mkdir(parents=True)
        (self.editor/'src-tauri/capabilities').mkdir()
        self.files = {
            'src-tauri/tauri.conf.json': json.dumps({'identifier':'org.hydra.generated-editor',
                'build':{'frontendDist':'../dist'},'bundle':{'active':True}}),
            'src-tauri/Cargo.toml':'[package]\nname="generated"\n[dependencies]\ntauri = { version = "2", features = [] }\n',
            'src-tauri/src/main.rs':'fn main(){ tauri::Builder::default().run(tauri::generate_context!()).unwrap(); }\n',
            'src-tauri/capabilities/default.json':json.dumps({'permissions':['core:default']})
        }
        for name, content in self.files.items():
            (self.editor/name).write_text(content)

    def test_preview_makes_no_mutation_and_does_not_certify(self):
        result=updater.configure(self.editor,PUBKEY,URL)
        self.assertEqual(result['status'],'PREVIEW_ONLY')
        self.assertFalse(result['signedUpdateExecutionCertified'])
        for name,content in self.files.items():self.assertEqual((self.editor/name).read_text(),content)

    def test_applies_real_tauri_v2_configuration_and_is_idempotent(self):
        result=updater.configure(self.editor,PUBKEY,URL,apply=True)
        self.assertEqual(len(result['changedFiles']),4)
        config=json.loads((self.editor/'src-tauri/tauri.conf.json').read_text())
        self.assertTrue(config['bundle']['createUpdaterArtifacts'])
        self.assertEqual(config['plugins']['updater']['pubkey'],PUBKEY)
        self.assertEqual(config['plugins']['updater']['endpoints'],[URL])
        cargo=(self.editor/'src-tauri/Cargo.toml').read_text()
        self.assertIn('tauri-plugin-updater = "2"',cargo)
        self.assertIn('tauri_plugin_updater::Builder::new().build()', (self.editor/'src-tauri/src/main.rs').read_text())
        permissions=json.loads((self.editor/'src-tauri/capabilities/default.json').read_text())['permissions']
        self.assertIn('updater:allow-download-and-install',permissions)
        self.assertEqual(updater.configure(self.editor,PUBKEY,URL,apply=True)['changedFiles'],[])

    def test_rotation_fails_closed(self):
        updater.configure(self.editor,PUBKEY,URL,apply=True)
        with self.assertRaisesRegex(ValueError,'trust settings differ'):
            updater.configure(self.editor,PUBKEY,'https://attacker.example.org/releases',apply=True)

    def test_http_endpoint_denied(self):
        for url in ['http://releases.example.org', 'https://localhost/update',
                    'https://user:pass@releases.example.org', 'https://releases.example.org/#frag']:
            with self.subTest(url=url),self.assertRaises(ValueError):
                updater.configure(self.editor,PUBKEY,url,apply=True)

    def test_wrong_key_type_denied(self):
        for key in ['not-base64',base64.b64encode(b'Ed25519 HYDRA signing receipt').decode()]:
            with self.subTest(key=key),self.assertRaises(ValueError):
                updater.configure(self.editor,key,URL,apply=True)

    def test_unsafe_editor_identity_denied(self):
        conf=self.editor/'src-tauri/tauri.conf.json'
        conf.write_text(json.dumps({'identifier':'com.unrelated.editor','build':{'frontendDist':'../dist'}}))
        with self.assertRaisesRegex(ValueError,'Not a generated HYDRA'):
            updater.configure(self.editor,PUBKEY,URL,apply=True)

    def test_symlinked_configuration_denied(self):
        file=self.editor/'src-tauri/tauri.conf.json'
        target=file.parent/'replacement.json'
        file.rename(target)
        try:file.symlink_to(target)
        except (OSError,NotImplementedError):self.skipTest('Symlinks prohibited')
        with self.assertRaisesRegex(ValueError,'Symbolic'):
            updater.configure(self.editor,PUBKEY,URL,apply=True)

    def test_invalid_minisign_record_denied(self):
        fake=base64.b64encode(b'untrusted comment: minisign public key: test\n' + base64.b64encode(b'Ed' + b'x'*25) + b'\n').decode()
        with self.assertRaisesRegex(ValueError,'Malformed'):
            updater.configure(self.editor,fake,URL,apply=True)

    def test_native_build_aborts_on_failed_real_rust_model_tests(self):
        from unittest import mock
        build_source = ROOT/'scripts/distribution/build_generated_editor.py'
        spec2=importlib.util.spec_from_file_location('hydra_native_build',build_source)
        builder=importlib.util.module_from_spec(spec2)
        sys.modules[spec2.name]=builder
        spec2.loader.exec_module(builder)
        (self.editor/'distribution').mkdir()
        (self.editor/'distribution/hydra-distribution.json').write_text(json.dumps({'version':'1.0.0'}))
        (self.editor/'src-tauri/target').mkdir()
        (self.editor/'src-tauri/src/model_store.rs').write_text(
            '#[cfg(test)] #[test] fn example() {} fn hydra_write_model() {}', encoding='utf-8')
        calls=[]
        def fake_bounded(argv, cwd, timeout):
            calls.append(argv)
            return {'status':'FAILED','exitCode':9}
        with mock.patch.object(builder,'bounded',side_effect=fake_bounded):
            result=builder.build(self.editor.parent,self.editor.name,True,False,True,False,60,False)
        self.assertFalse(result['qualified'])
        self.assertEqual(result['steps']['nativeModelTests']['status'],'FAILED')
        self.assertEqual(calls,[['cargo','test','--','--test-threads=1']])

    def test_native_build_refuses_zero_model_store_tests(self):
        from unittest import mock
        build_source = ROOT/'scripts/distribution/build_generated_editor.py'
        spec2=importlib.util.spec_from_file_location('hydra_native_missing',build_source)
        builder=importlib.util.module_from_spec(spec2)
        sys.modules[spec2.name]=builder
        spec2.loader.exec_module(builder)
        (self.editor/'distribution').mkdir()
        (self.editor/'distribution/hydra-distribution.json').write_text(json.dumps({'version':'1.0.0'}))
        with mock.patch.object(builder,'bounded',side_effect=AssertionError('Cargo must not be called')):
            result=builder.build(self.editor.parent,self.editor.name,True,False,True,False,60,False)
        self.assertEqual(result['steps']['nativeModelTests']['status'],'BLOCKED')
        self.assertFalse(result['qualified'])

    def test_glsp_workspace_matches_the_real_server_toolchain(self):
        path=ROOT/'src/main/java/com/example/editor/integration/adapter/glsp/EclipseGlspTechnologyAdapter.java'
        source=path.read_text(encoding='utf-8')
        self.assertIn('workspacePackage(packageName, serverLanguage)',source)
        match=re.search(r'private String workspacePackage\(.*?\n    }',source,re.S)
        self.assertIsNotNone(match)
        method=match.group(0).replace('private String workspacePackage','public static String workspacePackage',1)
        runner=Path(self.temp.name)/'WorkspaceTest.java'
        runner.write_text('public class WorkspaceTest {\n' + method +
            '\npublic static void main(String[] args) { System.out.println(workspacePackage("@hydra/model", "java")); System.out.println(workspacePackage("@hydra/model", "typescript")); }\n}',encoding='utf-8')
        subprocess.run(['javac','--release','21',str(runner)],check=True,capture_output=True,timeout=30)
        output=subprocess.run(['java','-cp',str(runner.parent),'WorkspaceTest'],check=True,capture_output=True,text=True,timeout=30).stdout
        first,second=[json.loads(x) for x in output.splitlines() if x.strip()]
        self.assertEqual(first['workspaces'],['client'])
        self.assertEqual(second['workspaces'],['client','server'])

    def test_java_generator_emits_windows_atomic_replacement_and_interprocess_lock(self):
        sources=ROOT/'src/main/java/com/example/editor/standalone/distribution'
        build=Path(self.temp.name)/'classes';build.mkdir()
        java=[sources/name for name in ('StandaloneDistributionArtifact.java','StandaloneDistributionPlanner.java',
            'StandaloneDistributionProfile.java','StandaloneDistributionTarget.java','TauriModelStoreTemplate.java')]
        test_java=Path(self.temp.name)/'Test.java'
        test_java.write_text('''import com.example.editor.standalone.distribution.*;
import java.util.*;
public class Test {public static void main(String[] args) {
 var p=new StandaloneDistributionProfile("Test Editor", "1.0.0", Set.of(StandaloneDistributionTarget.TAURI_WINDOWS),true,List.of(".hydra"),"stable");
 var files=new StandaloneDistributionPlanner().plan(p);
 String cargo=files.stream().filter(a->a.path().equals("src-tauri/Cargo.toml")).findFirst().orElseThrow().content();
 String rust=files.stream().filter(a->a.path().equals("src-tauri/src/model_store.rs")).findFirst().orElseThrow().content();
 if(!cargo.contains("fs2 = \\\"0.4\\\"") || !cargo.contains("windows-sys"))throw new AssertionError("Missing native libraries");
 for(var expected:List.of("lock_exclusive()","MoveFileExW", "MOVEFILE_REPLACE_EXISTING", "commit_revision(&staging, &path, exists)","create_new(true)")){
   if(!rust.contains(expected))throw new AssertionError(expected);
 }
 if(rust.contains("fs::rename(&staging, &path)"))throw new AssertionError("Unsafe unconditional rename");
 System.out.println("FIX184_REAL_GENERATOR=PASS");
}}''')
        subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(build),*(str(p) for p in java),str(test_java)],check=True,capture_output=True,timeout=45)
        result=subprocess.run(['java','-cp',str(build),'Test'],check=True,capture_output=True,text=True,timeout=20)
        self.assertIn('FIX184_REAL_GENERATOR=PASS',result.stdout)

class TransientReplaceRegression(unittest.TestCase):
    """Windows antivirus/indexer handles must not abort the updater configuration."""
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='hydra-fix184-replace-')
        self.addCleanup(self.temp.cleanup)
        root = Path(self.temp.name)
        self.target = root/'tauri.conf.json'
        self.target.write_text('old')
        self.staged = root/'.hydra-updater-test'
        self.staged.write_text('new')

    @staticmethod
    def denied():
        error = PermissionError(13, 'Access is denied')
        error.winerror = 5
        return error

    def test_transient_denial_is_retried_until_replaced(self):
        from unittest import mock
        native = os.replace
        calls = []
        def flaky(source, destination):
            calls.append(source)
            if len(calls) <= 2:
                raise self.denied()
            return native(source, destination)
        with mock.patch.object(updater.os, 'replace', flaky), mock.patch.object(updater.os, 'name', 'nt'), \
                mock.patch.object(updater.time, 'sleep') as pause:
            updater.replace_file(str(self.staged), self.target)
        self.assertEqual(self.target.read_text(), 'new')
        self.assertEqual((len(calls), pause.call_count), (3, 2))

    def test_persistent_denial_fails_after_bounded_retries(self):
        from unittest import mock
        with mock.patch.object(updater.os, 'replace', side_effect=self.denied()), mock.patch.object(updater.os, 'name', 'nt'), \
                mock.patch.object(updater.time, 'sleep') as pause:
            with self.assertRaises(PermissionError):
                updater.replace_file(str(self.staged), self.target, retries=3)
        self.assertEqual(pause.call_count, 3)
        self.assertEqual(self.target.read_text(), 'old')

    def test_other_errors_are_not_retried(self):
        from unittest import mock
        with mock.patch.object(updater.os, 'replace', side_effect=FileNotFoundError(2, 'missing')), \
                mock.patch.object(updater.time, 'sleep') as pause:
            with self.assertRaises(FileNotFoundError):
                updater.replace_file(str(self.staged), self.target)
        pause.assert_not_called()

if __name__ == '__main__':
    unittest.main(verbosity=2)
