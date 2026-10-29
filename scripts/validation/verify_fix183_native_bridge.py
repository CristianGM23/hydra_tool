#!/usr/bin/env python3
"""Pruebas focalizadas D10: salida real de planner Java y dist reproducible."""
from __future__ import annotations
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[2]
JAVA=ROOT/'src/main/java/com/example/editor/standalone/distribution'


def module():
    spec=importlib.util.spec_from_file_location('prepare_tauri',ROOT/'scripts/distribution/prepare_generated_tauri_frontend.py')
    import sys
    obj=importlib.util.module_from_spec(spec);sys.modules[spec.name]=obj;spec.loader.exec_module(obj)
    return obj

class NativeBridge(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='hydra-fix183-');self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name)/'editor';self.root.mkdir()
        self.front=self.root/'frontend';(self.front/'config').mkdir(parents=True)
        for name in ('index.html','app.js','native-transport.js','runtime.js','styles.css'):
            (self.front/name).write_text('// '+name,encoding='utf8')
        (self.front/'config/capabilities.json').write_text('[]',encoding='utf8')
        (self.root/'standalone-editor.json').write_text(json.dumps({'applicationId':'ed','editorId':'ed'}),encoding='utf8')
    def test_dist_contains_exact_bundled_assets_and_descriptor(self):
        result=module().prepare(self.root)
        self.assertEqual(result['status'],'PREPARED')
        self.assertEqual((self.root/'dist/standalone-editor.json').read_bytes(),(self.root/'standalone-editor.json').read_bytes())
        self.assertEqual(module().prepare(self.root)['status'],'UNCHANGED')
    def test_refuses_to_clobber_modified_dist(self):
        module().prepare(self.root)
        (self.root/'dist/app.js').write_text('MANUALLY EDITED')
        with self.assertRaisesRegex(ValueError,'Refusing'):
            module().prepare(self.root)
    def test_missing_generated_asset_blocks(self):
        (self.front/'native-transport.js').unlink()
        with self.assertRaisesRegex(ValueError,'native-transport.js'):
            module().prepare(self.root)
    def test_descriptor_must_match_editor_contract(self):
        (self.root/'standalone-editor.json').write_text('{}')
        with self.assertRaisesRegex(ValueError,'descriptor'):
            module().prepare(self.root)
    def test_symlink_denied(self):
        link=self.front/'link.js'
        try:link.symlink_to(self.front/'app.js')
        except (OSError,NotImplementedError):self.skipTest('Symlinks unavailable')
        with self.assertRaisesRegex(ValueError,'Symbolic link'):
            module().prepare(self.root)
    def test_real_java_readiness_requires_model_transport_assets(self):
        sources=['StandaloneDistributionDiagnostic.java','StandaloneDistributionDiagnosticSeverity.java',
                 'StandaloneDistributionValidationReport.java','StandaloneProductionReadinessService.java',
                 'StandaloneDistributionProfile.java','StandaloneDistributionTarget.java']
        harness=Path(self.tmp.name)/'ReadinessHarness.java'
        harness.write_text('''import java.nio.file.*;
import java.util.*;
import com.example.editor.standalone.distribution.*;
public class ReadinessHarness {
 public static void main(String[] args) throws Exception {
  Path root=Path.of(args[0]);
  var profile=new StandaloneDistributionProfile("Library","1.2.0",Set.of(StandaloneDistributionTarget.TAURI_WINDOWS),false,List.of(".hydra"),"beta");
  var accepted=new StandaloneDistributionValidationReport(true,List.of(),Map.of());
  var check=new StandaloneProductionReadinessService();
  String[] first={"dist/index.html","src-tauri/Cargo.toml","src-tauri/build.rs","src-tauri/src/main.rs","src-tauri/capabilities/default.json"};
  for(String f:first){Path p=root.resolve(f);Files.createDirectories(p.getParent());Files.writeString(p,f.endsWith("index.html")?"<html/>":"{}");}
  if(check.assess(root,profile,accepted).valid())throw new AssertionError("Old empty Tauri shell must fail");
  String[] newer={"src-tauri/src/model_store.rs","dist/native-transport.js","dist/standalone-editor.json","src-tauri/tauri.conf.json"};
  for(String f:newer){Path p=root.resolve(f);Files.createDirectories(p.getParent());Files.writeString(p,"{}");}
  if(!check.assess(root,profile,accepted).valid())throw new AssertionError("Complete source contract should pass static readiness");
  Files.delete(root.resolve("src-tauri/src/model_store.rs"));
  if(check.assess(root,profile,accepted).valid())throw new AssertionError("Deleted Rust transport must fail");
  System.out.println("FIX183_NATIVE_READINESS=PASS");
 }
}''',encoding='utf8')
        compiled=Path(self.tmp.name)/'readiness-out';compiled.mkdir()
        subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(compiled),*(str(JAVA/x) for x in sources),str(harness)],check=True,capture_output=True,timeout=60)
        result=subprocess.run(['java','-cp',str(compiled),'ReadinessHarness',str(self.root)],check=True,capture_output=True,text=True,timeout=30)
        self.assertIn('FIX183_NATIVE_READINESS=PASS',result.stdout)

    def test_real_java_planner_emits_bounded_ipc_commands(self):
        sources=['StandaloneDistributionArtifact.java','StandaloneDistributionPlanner.java',
                'StandaloneDistributionProfile.java','StandaloneDistributionTarget.java',
                'TauriModelStoreTemplate.java']
        harness=Path(self.tmp.name)/'PlannerHarness.java'
        harness.write_text('''
import com.example.editor.standalone.distribution.*;
import java.util.*;
public class PlannerHarness {
 public static void main(String[] args) {
  var profile=new StandaloneDistributionProfile("HYDRA Library Editor","1.2.0",Set.of(StandaloneDistributionTarget.TAURI_WINDOWS),true,List.of(".hydra"),"beta");
  var files=new StandaloneDistributionPlanner().plan(profile);
  var rust=files.stream().filter(x->x.path().equals("src-tauri/src/model_store.rs")).findFirst().orElseThrow().content();
  var main=files.stream().filter(x->x.path().equals("src-tauri/src/main.rs")).findFirst().orElseThrow().content();
  var config=files.stream().filter(x->x.path().equals("src-tauri/tauri.conf.json")).findFirst().orElseThrow().content();
  var cargo=files.stream().filter(x->x.path().equals("src-tauri/Cargo.toml")).findFirst().orElseThrow().content();
  for(var command:List.of("hydra_list_models","hydra_read_model","hydra_write_model","hydra_delete_model")){
   if(!rust.contains("fn "+command+"(")||!main.contains(command))throw new AssertionError(command);
  }
  if(!rust.contains("If-Match")||!rust.contains("create_new(true)")||!rust.contains("app_local_data_dir"))throw new AssertionError("storage contract");
  if(!config.contains("withGlobalTauri\\\": true")||!cargo.contains("serde_json")||!cargo.contains("sha2"))throw new AssertionError("build config");
  System.out.println("FIX183_REAL_PLANNER=PASS artifacts="+files.size());
 }
}''',encoding='utf8')
        compiled=Path(self.tmp.name)/'out';compiled.mkdir()
        subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(compiled),*(str(JAVA/x) for x in sources),str(harness)],check=True,capture_output=True,timeout=60)
        result=subprocess.run(['java','-cp',str(compiled),'PlannerHarness'],check=True,capture_output=True,text=True,timeout=30)
        self.assertIn('FIX183_REAL_PLANNER=PASS',result.stdout)

if __name__=='__main__':unittest.main(verbosity=2)
