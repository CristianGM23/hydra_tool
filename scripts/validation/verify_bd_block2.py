#!/usr/bin/env python3
"""B+D block 2: executable Python tests and isolated real Java compilation/harness."""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import subprocess
import shutil
import tempfile
import hashlib
import zipfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'src/main/java/com/example/editor'


def load(name: str, relative: str):
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    module = importlib.util.module_from_spec(spec)
    import sys
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


builder = load('bd2_builder', 'scripts/distribution/build_generated_editor.py')
matrix = load('bd2_matrix', 'scripts/validation/run_native_e2e_matrix.py')
webverify = load('bd2_webverify', 'scripts/distribution/verify_web_package.py')


class BD2FunctionalTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='hydra-bd2-test-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.editor = self.root / 'trusted-editor'
        (self.editor / 'distribution').mkdir(parents=True)
        (self.editor / 'dist' / 'assets').mkdir(parents=True)
        (self.editor / 'public').mkdir(parents=True)
        (self.editor / 'distribution' / 'hydra-distribution.json').write_text(json.dumps({
            'version': '2.0.0', 'targets': ['PWA'], 'offlineEnabled': True}), encoding='utf-8')
        (self.editor / 'dist' / 'index.html').write_text('<script src="./assets/app.js"></script>', encoding='utf-8')
        (self.editor / 'dist' / 'assets' / 'app.js').write_text('console.log("HYDRA");', encoding='utf-8')
        (self.editor / 'public' / 'manifest.webmanifest').write_text('{"name":"HYDRA Editor"}', encoding='utf-8')

    def test_secure_root_works(self):
        self.assertEqual(builder.checked_root(self.root, 'trusted-editor'), self.root.resolve() / 'trusted-editor')

    def test_traversal_denied(self):
        for path in ('../escape', '/tmp', 'C:/unsafe', 'folder\\name'):
            with self.subTest(path=path), self.assertRaises(ValueError):
                builder.checked_root(self.root, path)

    def test_symlink_root_denied(self):
        link = self.root / 'linked-editor'
        try:
            link.symlink_to(self.editor, target_is_directory=True)
        except (OSError, NotImplementedError):
            self.skipTest('Symlinks unavailable')
        with self.assertRaises(ValueError):
            builder.checked_root(self.root, 'linked-editor')

    def test_inventory_requires_entry(self):
        (self.editor / 'dist' / 'index.html').unlink()
        with self.assertRaisesRegex(ValueError, 'index.html'):
            builder.inventory(self.editor / 'dist')

    def test_inventory_rejects_secrets(self):
        (self.editor / 'dist' / '.env').write_text('API_PASSWORD=abc')
        with self.assertRaisesRegex(ValueError, 'Sensitive'):
            builder.inventory(self.editor / 'dist')

    def test_inventory_rejects_source_maps(self):
        (self.editor / 'dist' / 'assets' / 'app.js.map').write_text('{}')
        with self.assertRaisesRegex(ValueError, 'Sensitive'):
            builder.inventory(self.editor / 'dist')

    def test_inventory_rejects_reserved_manifest(self):
        (self.editor / 'dist' / 'hydra-deployment.json').write_text('{}')
        with self.assertRaisesRegex(ValueError, 'reserved'):
            builder.inventory(self.editor / 'dist')

    def test_offline_precache_binds_asset_hash(self):
        (self.editor / 'dist' / 'manifest.webmanifest').write_text('{"name":"HYDRA"}')
        cache, assets = builder.precache(self.editor / 'dist', '2.0.0')
        worker = (self.editor / 'dist' / 'service-worker.js').read_text()
        self.assertIn('assets/app.js', worker)
        self.assertIn("url.pathname.startsWith('/api/')", worker)
        self.assertIn('encodeURIComponent(self.registration.scope)', worker)
        self.assertIn("url.origin !== self.location.origin", worker)
        self.assertIn('caches.keys()', worker)
        self.assertIn('index.html', assets)
        (self.editor / 'dist' / 'assets' / 'app.js').write_text('console.log("changed");')
        second, _ = builder.precache(self.editor / 'dist', '2.0.0')
        self.assertNotEqual(cache, second)

    def test_offline_preparation_build_reports_real_assets(self):
        report = builder.build(self.root, 'trusted-editor', False, True, False, True, 60, True)
        self.assertFalse(report['qualified'])  # Existing assets inspected, but actual build was not executed here.
        self.assertEqual(report['steps']['webBuild']['status'], 'NOT_EXECUTED')
        self.assertEqual(report['steps']['offlineShell']['status'], 'GENERATED')
        self.assertEqual(report['steps']['webAssets']['status'], 'PASSED')
        self.assertIn('service-worker.js', builder.inventory(self.editor / 'dist'))

    def test_build_never_infers_native_success(self):
        (self.editor / 'src-tauri').mkdir()
        (self.editor / 'src-tauri' / 'Cargo.toml').write_text('[package]\nname="fixture"\n')
        report = builder.build(self.root, 'trusted-editor', False, True, True, False, 60, False)
        self.assertFalse(report['qualified'])
        self.assertEqual(report['steps']['nativeBuild']['status'], 'NOT_EXECUTED')

    def test_missing_web_build_never_qualified(self):
        (self.editor / 'dist' / 'index.html').unlink()
        report = builder.build(self.root, 'trusted-editor', False, True, False, False, 60, False)
        self.assertFalse(report['qualified'])
        self.assertEqual(report['steps']['webAssets']['status'], 'FAILED')

    def test_native_matrix_requires_real_fixture(self):
        report = matrix.run('xtext', self.editor, 60, True)
        self.assertEqual(report['status'], 'UNAVAILABLE')
        self.assertEqual(report['provenCapabilities'], [])

    def test_native_matrix_never_proves_on_dry_run(self):
        (self.editor / 'pom.xml').write_text('<project/>')
        report = matrix.run('xtext', self.editor, 60, False)
        self.assertEqual(report['status'], 'UNVERIFIED')
        self.assertEqual(report['stages'][0]['status'], 'NOT_EXECUTED')
        self.assertFalse(report['functionalParityCertified'])

    def test_native_matrix_pass_but_never_claims_full_parity(self):
        (self.editor / 'pom.xml').write_text('<project/>')
        mock = subprocess.CompletedProcess(['mvn'], 0, stdout='[INFO] BUILD SUCCESS')
        with patch.object(matrix.shutil, 'which', return_value='mvn'), patch.object(matrix.subprocess, 'run', return_value=mock) as invoked:
            report = matrix.run('xtext', self.editor, 30, True)
            self.assertEqual(report['status'], 'PASSED')
            self.assertEqual(report['provenCapabilities'], ['NATIVE_BUILD'])
            self.assertFalse(report['functionalParityCertified'])
            self.assertFalse(invoked.call_args.kwargs['shell'])

    def test_native_matrix_reports_unavailable_tool(self):
        (self.editor / 'pom.xml').write_text('<project/>')
        with patch.object(matrix.shutil, 'which', return_value=None):
            report = matrix.run('emf', self.editor, 30, True)
        self.assertEqual(report['status'], 'UNVERIFIED')
        self.assertEqual(report['stages'][0]['status'], 'TOOL_UNAVAILABLE')

    def test_native_matrix_rejects_path_escape(self):
        with self.assertRaises(ValueError):
            matrix.locate(self.root, '../unsafe')

    def test_native_matrix_can_run_more_than_one_stage(self):
        (self.editor / 'package.json').write_text('{"scripts":{}}')
        (self.editor / 'package-lock.json').write_text('{}')
        results = iter([
            subprocess.CompletedProcess([], 0, stdout='install okay'),
            subprocess.CompletedProcess([], 0, stdout='build okay'),
            subprocess.CompletedProcess([], 0, stdout='test okay'),
        ])
        with patch.object(matrix.shutil, 'which', return_value='npm'), patch.object(matrix.subprocess, 'run', side_effect=lambda *args, **kwargs: next(results)) as invoked:
            report = matrix.run('langium', self.editor, 30, True)
        self.assertEqual(report['status'], 'PASSED')
        self.assertEqual(len(report['stages']), 3)
        self.assertEqual(invoked.call_count, 3)

    def test_no_shell_in_project_build(self):
        (self.editor / 'package.json').write_text('{"scripts":{"build":"node build.js"}}')
        (self.editor / 'package-lock.json').write_text('{}')
        mock = subprocess.CompletedProcess([], 0, stdout='okay')
        with patch.object(builder.shutil, 'which', return_value='npm'), patch.object(builder.subprocess, 'run', return_value=mock) as invoked:
            report = builder.build(self.root, 'trusted-editor', True, True, False, False, 30, False)
        self.assertTrue(report['qualified'])
        self.assertEqual(invoked.call_count, 2)
        self.assertFalse(invoked.call_args.kwargs['shell'])

    def test_deployable_web_archive_verification_and_mutation(self):
        package = self.root / 'compiled-web.zip'
        content = b'<html>HYDRA</html>'
        sha = hashlib.sha256(content).hexdigest()
        metadata = json.dumps({'level': 'DEPLOYABLE_WEB_ASSETS', 'version': '1.0.0',
                               'nativeInstallerCertified': False, 'files': 1}).encode()
        with zipfile.ZipFile(package, 'w') as zip:
            zip.writestr('index.html', content)
            zip.writestr('hydra-release.sha256', f'{sha}  index.html\n')
            zip.writestr('hydra-deployment.json', metadata)
        self.assertEqual(webverify.verify(package)['status'], 'PASSED')
        with zipfile.ZipFile(package, 'w') as zip:
            zip.writestr('index.html', b'CHANGED')
            zip.writestr('hydra-release.sha256', f'{sha}  index.html\n')
            zip.writestr('hydra-deployment.json', metadata)
        with self.assertRaisesRegex(ValueError, 'checksum'):
            webverify.verify(package)

    def test_web_archive_rejects_traversal_and_duplicates(self):
        package = self.root / 'malicious.zip'
        metadata = json.dumps({'level': 'DEPLOYABLE_WEB_ASSETS', 'nativeInstallerCertified': False, 'files': 2})
        with zipfile.ZipFile(package, 'w') as zip:
            zip.writestr('index.html', '<html/>')
            zip.writestr('../escape', 'unsafe')
            zip.writestr('hydra-release.sha256', '')
            zip.writestr('hydra-deployment.json', metadata)
        with self.assertRaisesRegex(ValueError, 'Unsafe archive path'):
            webverify.verify(package)
        with zipfile.ZipFile(package, 'w') as zip:
            zip.writestr('index.html', '<html/>')
            import warnings
            with warnings.catch_warnings():
                warnings.simplefilter('ignore', UserWarning)
                zip.writestr('index.html', '<html/>')
            zip.writestr('hydra-release.sha256', '')
            zip.writestr('hydra-deployment.json', metadata)
        with self.assertRaisesRegex(ValueError, 'duplicate entries'):
            webverify.verify(package)

    def test_native_build_requires_actual_installer_artifacts(self):
        (self.editor / 'src-tauri').mkdir()
        (self.editor / 'src-tauri' / 'Cargo.toml').write_text('[package]\nname="fixture"\n')
        mock = subprocess.CompletedProcess([], 0, stdout='cargo build succeeded')
        with patch.object(builder.shutil, 'which', return_value='cargo'), patch.object(builder.subprocess, 'run', return_value=mock):
            report = builder.build(self.root, 'trusted-editor', True, False, True, False, 60, False)
        self.assertFalse(report['qualified'])
        self.assertEqual(report['steps']['nativeBuild']['status'], 'FAILED')
        self.assertIn('bundle', report['steps']['nativeBuild']['reason'])

    def test_web_package_rejects_misleading_certification(self):
        package = self.root / 'wrong-grade.zip'
        content = b'index'
        digest = hashlib.sha256(content).hexdigest()
        with zipfile.ZipFile(package, 'w') as zip:
            zip.writestr('index.html', content)
            zip.writestr('hydra-release.sha256', f'{digest}  index.html\n')
            zip.writestr('hydra-deployment.json', json.dumps({
                'level': 'NATIVE_INSTALLER_SIGNED', 'version': '1.0.0', 'files': 1,
                'nativeInstallerCertified': True}))
        with self.assertRaisesRegex(ValueError, 'qualification'):
            webverify.verify(package)

    def test_symlinks_in_distribution_rejected(self):
        link = self.editor / 'dist' / 'asset-link.js'
        try:
            link.symlink_to(self.editor / 'dist' / 'assets' / 'app.js')
        except (OSError, NotImplementedError):
            self.skipTest('Symlinks unavailable')
        with self.assertRaisesRegex(ValueError, 'symbolic'):
            builder.inventory(self.editor / 'dist')


def run_java():
    javafiles = ['io/AtomicFiles.java', *['standalone/distribution/' + name + '.java' for name in (
        'StandaloneDistributionPackageResult', 'StandaloneDistributionProfile',
        'StandaloneDistributionTarget', 'StandaloneDistributionDiagnostic',
        'StandaloneDistributionDiagnosticSeverity', 'StandaloneDistributionValidationReport',
        'StandaloneWebDeploymentPackager', 'StandaloneProductionReadinessService')]]
    with tempfile.TemporaryDirectory(prefix='hydra-bd2-java-') as directory:
        sources = [str(JAVA / file) for file in javafiles] + [str(ROOT / 'scripts/testing/bd-block2/BD2Harness.java')]
        for command in (['javac', '--release', '21', '-d', directory, *sources],
                        ['java', '-cp', directory, 'BD2Harness']):
            run = subprocess.run(command, capture_output=True, text=True, cwd=ROOT, timeout=120)
            if run.returncode:
                raise RuntimeError('Java B2+D2 test failed:\n' + run.stdout + '\n' + run.stderr)
            if 'BD2Harness' in command:
                print(run.stdout.strip())


if __name__ == '__main__':
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(BD2FunctionalTests)
    passed = unittest.TextTestRunner(verbosity=1).run(suite)
    if not passed.wasSuccessful():
        raise SystemExit(1)
    run_java()
    # A true end-to-end generated-editor path, exercised on a temporary copy.
    # Does not mutate the checked-out canonical fixture or introduce generated extras.
    with tempfile.TemporaryDirectory(prefix='hydra-bd2-e2e-') as location:
        allowed = Path(location)
        editor = allowed / 'generated-editor'
        shutil.copytree(ROOT / 'reference-projects/bd2-standalone-smoke', editor,
                        ignore=shutil.ignore_patterns('dist', 'node_modules', 'target'))
        run = builder.build(allowed, 'generated-editor', True, True, False, True, 120, True)
        if not run['qualified']:
            raise RuntimeError('Real generated-editor build failed: ' + json.dumps(run, indent=2))
        classes = allowed / 'classes'
        classes.mkdir()
        sources = [str(JAVA / file) for file in ['io/AtomicFiles.java', *[
            'standalone/distribution/' + name + '.java' for name in (
                'StandaloneDistributionPackageResult', 'StandaloneDistributionProfile',
                'StandaloneDistributionTarget', 'StandaloneDistributionDiagnostic',
                'StandaloneDistributionDiagnosticSeverity', 'StandaloneDistributionValidationReport',
                'StandaloneWebDeploymentPackager', 'StandaloneProductionReadinessService')]]]
        sources.append(str(ROOT / 'scripts/testing/bd-block2/BD2RealBundleHarness.java'))
        compile = subprocess.run(['javac', '--release', '21', '-d', str(classes), *sources],
                                 capture_output=True, text=True, timeout=120)
        if compile.returncode:
            raise RuntimeError('Generated fixture integration compilation failed: ' + compile.stderr)
        archive = allowed / 'independent-web.zip'
        call = subprocess.run(['java', '-cp', str(classes), 'BD2RealBundleHarness', str(editor), str(archive)],
                              capture_output=True, text=True, timeout=120)
        if call.returncode:
            raise RuntimeError('Generated fixture packaging failed: ' + call.stderr + call.stdout)
        print(call.stdout.strip())
        verified = webverify.verify(archive)
        if verified['status'] != 'PASSED' or verified['fileCount'] < 4:
            raise RuntimeError('Independent web package verification failed')
        print('BD2_REAL_E2E=PASS node_build=PASS readiness=PASS web_zip=PASS sha256=PASS')
    print(f'BD_BLOCK2=PASS python_cases={passed.testsRun} java_harness=PASS real_generated_editor=PASS')
