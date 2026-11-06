#!/usr/bin/env python3
"""Fix182 stage 1: real isolated Java21 path regression + generated-export wiring guards.

External Sirius/GLSP, Xtext/BESSER runtimes and native OS installers are NOT
certified by this focused validation. Full Maven/React tests remain separate.
"""
from __future__ import annotations
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
PACKAGE=ROOT/'src/main/java/com/example/editor/integration'

JAVA='''import java.nio.file.*;
import com.example.editor.integration.roundtrip.RoundTripSafePaths;
public class Smoke {
 public static void main(String[] args) throws Exception {
  Path root=Files.createTempDirectory("hydra-fix182-safe-");
  Path safe=RoundTripSafePaths.resolve(root,"generated/editor/model.json");
  if(!safe.startsWith(root)||!safe.endsWith("model.json"))throw new AssertionError("safe");
  String[] invalid={"../outside", "a/../outside","C:/escape", "/etc/passwd", "a//b", "a/./b","a\\\\..\\\\escape"};
  for(String name:invalid){try{RoundTripSafePaths.resolve(root,name);throw new AssertionError(name);}catch(com.example.editor.integration.compiler.TechnologyCompilationException expected){}}
  Path outside=Files.createTempDirectory("hydra-fix182-out-");
  Path link=root.resolve("link");
  try {Files.createSymbolicLink(link,outside);
   try{RoundTripSafePaths.resolve(root,"link/file.json");throw new AssertionError("symlink escape");}
   catch(com.example.editor.integration.compiler.TechnologyCompilationException expected){}
  }catch(UnsupportedOperationException|java.nio.file.FileSystemException|SecurityException e){System.out.println("SYMLINK_CASE=UNAVAILABLE");}
  System.out.println("FIX182_JAVA_PATHS=PASS");
 }
}'''

class FocusedContract(unittest.TestCase):
    def test_java21_real_safe_paths_and_symlinks(self):
        with tempfile.TemporaryDirectory() as d:
            temp=Path(d);(temp/'Smoke.java').write_text(JAVA)
            sources=[PACKAGE/'compiler/TechnologyCompilationException.java',
                     PACKAGE/'roundtrip/RoundTripSafePaths.java',temp/'Smoke.java']
            subprocess.run(['javac','-encoding','UTF-8','-d',str(temp),*(str(v) for v in sources)],check=True,timeout=45,capture_output=True)
            test=subprocess.run(['java','-cp',str(temp),'Smoke'],check=True,timeout=30,capture_output=True,text=True)
            self.assertIn('FIX182_JAVA_PATHS=PASS',test.stdout)
    def test_glsp_and_sirius_emit_conservative_manifest(self):
        for dest in ('adapter/glsp/EclipseGlspTechnologyAdapter.java','adapter/sirius/SiriusDesktopTechnologyAdapter.java'):
            source=(PACKAGE/dest).read_text()
            self.assertIn('TechnologyFidelityManifestSupport.json(mapper, request, editor',source)
            self.assertIn('hydra-fidelity-obligations.json',source)
        source=(PACKAGE/'compiler/TechnologyFidelityManifestSupport.java').read_text()
        for marker in ('CANONICAL_ONLY','NOT_EXECUTED','functionalParityCertified','OFFICIAL_TOOLCHAIN','sourceSha256'):
            self.assertIn(marker,source)
    def test_roundtrip_stale_and_post_edit_guards_exist(self):
        source=(PACKAGE/'roundtrip/resolution/RoundTripResolutionWorkflowService.java').read_text()
        for marker in ('changed since round-trip preview','changed after round-trip apply',
                       'backup altered','beforeHashes','afterHashes','Files.createDirectory(backupRoot)'):
            self.assertIn(marker,source)
    def test_browser_displays_but_cannot_attest(self):
        view=(ROOT/'frontend/src/distribution-center/NativeEvidenceStatusPanel.tsx').read_text()
        center=(ROOT/'frontend/src/distribution-center/StandaloneDistributionCenter.tsx').read_text()
        self.assertIn('Browser import does not verify signatures',view)
        self.assertIn('<NativeEvidenceStatusPanel />',center)
    def test_documents_do_not_claim_external_parity(self):
        source=(ROOT/'docs/development/HYDRA_FIX182_STAGE1_B12_B13_D12_D13.md').read_text()
        self.assertIn('NOT_CERTIFIED',source)
        self.assertIn('Fix181',source)

if __name__=='__main__':unittest.main(verbosity=2)
