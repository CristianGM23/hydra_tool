#!/usr/bin/env python3
"""Compile the actual GLSP Java emitter and exercise its emitted Node storage."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest

ROOT=Path(__file__).resolve().parents[2]
SOURCE=ROOT/'src/main/java/com/example/editor/integration/adapter/glsp/EclipseGlspTechnologyAdapter.java'
TEST=ROOT/'scripts/testing/fix185/glsp_storage_runtime.cjs'

class GeneratedGlspStorage(unittest.TestCase):
    def test_live_generated_typescript_storage_behaviors(self):
        with tempfile.TemporaryDirectory(prefix='hydra-fix185-glsp-') as directory:
            root=Path(directory)
            source=SOURCE.read_text(encoding='utf-8')
            begin=source.index('private String nodeStorage(){return """')
            end=source.index('""";}',begin)+len('""";}')
            method=source[begin:end].replace('private String nodeStorage()', 'static String nodeStorage()',1)
            runner=root/'HydraGlspStorageEmitter.java'
            runner.write_text('public class HydraGlspStorageEmitter {\n'+method+
                '\npublic static void main(String[] args){System.out.print(nodeStorage());}\n}',encoding='utf-8')
            subprocess.run(['javac','--release','21',str(runner)],cwd=root,check=True,timeout=45,capture_output=True,text=True)
            generated=subprocess.run(['java','-cp',str(root),'HydraGlspStorageEmitter'],check=True,timeout=45,capture_output=True,text=True).stdout
            ts=root/'hydra-source-model-storage.ts';ts.write_text(generated,encoding='utf-8')
            node_env=dict(os.environ)
            if not node_env.get('HYDRA_TYPESCRIPT'):
                # Prefer the project's pinned dependency; npm global may be empty
                # even when the frontend has been installed successfully.
                candidates=[ROOT/'frontend/node_modules/typescript',ROOT/'node_modules/typescript']
                for candidate in candidates:
                    if (candidate/'lib/typescript.js').is_file():
                        node_env['HYDRA_TYPESCRIPT']=str(candidate)
                        break
                else:
                    # Retain compatibility with machines using global TypeScript,
                    # but never manufacture a path to a missing global package.
                    try:
                        npm=subprocess.run(['npm.cmd' if os.name=='nt' else 'npm','root','-g'],
                                           capture_output=True,text=True,timeout=15,check=False)
                        if npm.returncode==0:
                            global_ts=Path(npm.stdout.strip())/'typescript'
                            if (global_ts/'lib/typescript.js').is_file():
                                node_env['HYDRA_TYPESCRIPT']=str(global_ts)
                    except (OSError,subprocess.TimeoutExpired):
                        pass
            result=subprocess.run(['node',str(TEST),str(ts)],cwd=root,env=node_env,capture_output=True,text=True,timeout=45)
            self.assertEqual(result.returncode,0,result.stdout+'\n'+result.stderr)
            self.assertIn('FIX185_REAL_GENERATED_GLSP_STORAGE=PASS',result.stdout)

if __name__=='__main__':unittest.main(verbosity=2)
