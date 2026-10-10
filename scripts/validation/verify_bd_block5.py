#!/usr/bin/env python3
"""Fix178 B5+D5: executable capability portfolio and secure local editor runtime."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import sys
import tempfile
import threading
import unittest
from urllib.request import Request, urlopen
from urllib.error import HTTPError
from zipfile import ZipFile, ZIP_DEFLATED

BASE=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(BASE/'scripts/validation'))
sys.path.insert(0,str(BASE/'scripts/distribution'))
from run_native_parity_portfolio import read_policy, assess
from verify_editor_runtime_package import verify as runtime_preflight, resolve_reference
from editor_release_registry import promote
from serve_registered_editor import runtime, open_server, request_file, load_checked


class PortfolioTests(unittest.TestCase):
    def setUp(self):
        temp=tempfile.TemporaryDirectory(prefix='hydra-bd5-policy-')
        self.addCleanup(temp.cleanup)
        self.root=Path(temp.name)
        self.policy={'schemaVersion':1,'groups':[{'id':'graphical-languages',
            'requiredProfiles':['emf','langium'], 'requireAllMembers':True,
            'capabilityCoverage':{'VALIDATION':{'minProfiles':2,'minScenarios':1},
                                  'QUICK_FIX':{'minProfiles':2,'minScenarios':1}}}]}
        self.path=self.root/'policy.json'
        self.save()
        self.member=lambda profile:{'id':profile,'profile':profile,'status':'PASSED',
            'certifiedCapabilities':['VALIDATION','QUICK_FIX'],
            'scenarioEvidence':[{'id':'validate','capability':'VALIDATION','evidenceSHA256':'a'*64},
                                {'id':'repair','capability':'QUICK_FIX','evidenceSHA256':'b'*64}]}
        self.matrix={'schemaVersion':1,'status':'PASSED','groups':[{'id':'graphical-languages',
           'status':'PASSED','semanticDisagreements':[],'scenarioIds':['validate','repair'],
           'members':[self.member('emf'),self.member('langium')]}]}

    def save(self):self.path.write_text(json.dumps(self.policy))

    def test_native_policy_loads(self):self.assertEqual(len(read_policy(self.path)['groups']),1)
    def test_fresh_native_execution_can_certify_scope(self):
        result=assess(read_policy(self.path),self.matrix,executed=True)
        self.assertEqual(result['status'],'CERTIFIED')
        self.assertFalse(result['universalTechnologyParityCertified'])
    def test_saved_reports_never_certify(self):
        self.assertEqual(assess(read_policy(self.path),self.matrix,executed=False)['status'],'NOT_CERTIFIED')
    def test_absent_toolchain_never_certifies(self):
        self.matrix['groups'][0]['members'][1]['status']='UNVERIFIED'
        self.assertEqual(assess(read_policy(self.path),self.matrix,executed=True)['status'],'NOT_CERTIFIED')
    def test_only_one_capability_is_insufficient(self):
        self.matrix['groups'][0]['members'][1]['certifiedCapabilities']=['VALIDATION']
        self.assertEqual(assess(read_policy(self.path),self.matrix,executed=True)['status'],'NOT_CERTIFIED')
    def test_missing_proof_fails(self):
        self.matrix['groups'][0]['members'][1]['scenarioEvidence']=[]
        self.assertEqual(assess(read_policy(self.path),self.matrix,executed=True)['status'],'NOT_CERTIFIED')
    def test_malformed_digest_fails(self):
        self.matrix['groups'][0]['members'][0]['scenarioEvidence'][0]['evidenceSHA256']='bad'
        self.assertEqual(assess(read_policy(self.path),self.matrix,executed=True)['status'],'NOT_CERTIFIED')
    def test_disagreement_fails(self):
        self.matrix['groups'][0]['semanticDisagreements']=['divergent output']
        self.assertEqual(assess(read_policy(self.path),self.matrix,executed=True)['status'],'NOT_CERTIFIED')
    def test_requirement_without_group_fails(self):
        self.matrix['groups']=[]
        self.assertEqual(assess(read_policy(self.path),self.matrix,executed=True)['status'],'NOT_CERTIFIED')
    def test_wrong_profiles_fails(self):
        self.matrix['groups'][0]['members'][1]['profile']='xtext'
        self.assertEqual(assess(read_policy(self.path),self.matrix,executed=True)['status'],'NOT_CERTIFIED')
    def test_two_scattered_scenarios_not_enough_for_minimum_two(self):
        self.policy['groups'][0]['capabilityCoverage']['VALIDATION']['minScenarios']=2
        self.save()
        self.assertEqual(assess(read_policy(self.path),self.matrix,executed=True)['status'],'NOT_CERTIFIED')
    def test_policy_symlink_blocked(self):
        link=self.root/'shortcut'
        try: link.symlink_to(self.path)
        except OSError: self.skipTest('Symlinks unavailable')
        with self.assertRaisesRegex(ValueError,'linked'):read_policy(link)
    def test_duplicate_profiles_blocked(self):
        self.policy['groups'][0]['requiredProfiles']=['emf','emf'];self.save()
        with self.assertRaisesRegex(ValueError,'unique'):read_policy(self.path)
    def test_single_platform_blocked(self):
        self.policy['groups'][0]['requiredProfiles']=['emf'];self.save()
        with self.assertRaisesRegex(ValueError,'two'):read_policy(self.path)
    def test_unknown_capability_blocked(self):
        self.policy['groups'][0]['capabilityCoverage']={'HACK':{'minProfiles':2,'minScenarios':1}}
        self.save()
        with self.assertRaisesRegex(ValueError,'Unknown'):read_policy(self.path)
    def test_no_boolean_count(self):
        self.policy['groups'][0]['capabilityCoverage']['VALIDATION']['minScenarios']=True
        self.save()
        with self.assertRaisesRegex(ValueError,'bounded'):read_policy(self.path)
    def test_policy_malformed_group_count(self):
        self.policy['groups']=[];self.save()
        with self.assertRaisesRegex(ValueError,'1..10'):read_policy(self.path)
    def test_partial_member_policy_blocked(self):
        self.policy['groups'][0]['requireAllMembers']=False;self.save()
        with self.assertRaisesRegex(ValueError,'Partial'):read_policy(self.path)
    def test_missing_inventory_blocked(self):
        self.matrix['groups'][0].pop('scenarioIds')
        self.assertEqual(assess(read_policy(self.path),self.matrix,executed=True)['status'],'NOT_CERTIFIED')
    def test_evidence_scenario_must_be_declared(self):
        self.matrix['groups'][0]['members'][1]['scenarioEvidence'][0]['id']='fabricated'
        self.assertEqual(assess(read_policy(self.path),self.matrix,executed=True)['status'],'NOT_CERTIFIED')
    def test_native_report_must_pass_globally(self):
        self.matrix['status']='UNVERIFIED'
        self.assertEqual(assess(read_policy(self.path),self.matrix,executed=True)['status'],'NOT_CERTIFIED')
    def test_unknown_native_profile_policy_rejected(self):
        self.policy['groups'][0]['requiredProfiles']=['emf','unknown-profile']
        self.save()
        with self.assertRaisesRegex(ValueError,'unique profiles'):read_policy(self.path)
    def test_malformed_native_report_rejected(self):
        with self.assertRaisesRegex(ValueError,'report'):assess(self.policy,{'status':'PASSED'},executed=True)


class DeploymentTests(unittest.TestCase):
    def setUp(self):
        temp=tempfile.TemporaryDirectory(prefix='hydra-bd5-runtime-')
        self.addCleanup(temp.cleanup)
        self.root=Path(temp.name)
        self.registry=self.root/'registry'
        self.package=self.root/'web.zip'
        self.files={'index.html':b'<!doctype html><html><head><link rel="manifest" href="./manifest.webmanifest"></head><body><script type="module" src="./assets/app.js"></script></body></html>',
            'assets/app.js':b'window.hydraEditor = true;',
            'manifest.webmanifest':b'{"name":"HYDRA Editor","display":"standalone"}',
            'service-worker.js':b'const SHELL=[]; caches.open("x"); self.addEventListener("fetch",()=>{});'}
        self.repackage()

    def repackage(self):
        with ZipFile(self.package,'w',ZIP_DEFLATED) as z:
            for name,body in sorted(self.files.items()):z.writestr(name,body)
            lines=''.join(hashlib.sha256(content).hexdigest()+'  '+name+'\n' for name,content in sorted(self.files.items()))
            z.writestr('hydra-release.sha256',lines)
            z.writestr('hydra-deployment.json',json.dumps({'schemaVersion':'1.0','level':'DEPLOYABLE_WEB_ASSETS',
                'version':'1.0.0','updateChannel':'stable','offlineEnabled':True,
                'files':len(self.files),'nativeInstallerCertified':False}))

    def publish(self):
        promote(self.registry,'editor-example','stable',self.package)
        return runtime(self.registry,'editor-example','stable')

    def serve(self):
        state=self.publish()
        server=open_server(state,0)
        thread=threading.Thread(target=server.serve_forever,daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        return state,server,'http://127.0.0.1:'+str(server.server_address[1])

    def get(self,url,**headers):
        return urlopen(Request(url,headers=headers),timeout=3)

    def test_deployable_compiled_pwa_preflight(self):
        result=runtime_preflight(self.package)
        self.assertTrue(result['offlineShellPackaged'])
        self.assertEqual(result['status'],'PASSED')
    def test_rejects_external_script(self):
        self.files['index.html']=b'<html><script src="https://tracker.example/e.js"></script></html>'
        self.repackage()
        with self.assertRaisesRegex(ValueError,'Remote'):runtime_preflight(self.package)
    def test_rejects_missing_linked_script(self):
        del self.files['assets/app.js'];self.repackage()
        with self.assertRaisesRegex(ValueError,'Missing'):runtime_preflight(self.package)
    def test_rejects_missing_worker(self):
        del self.files['service-worker.js'];self.repackage()
        with self.assertRaisesRegex(ValueError,'Offline'):runtime_preflight(self.package)
    def test_rejects_invalid_worker(self):
        self.files['service-worker.js']=b'addEventListener("fetch",function() {})';self.repackage()
        with self.assertRaisesRegex(ValueError,'incomplete'):runtime_preflight(self.package)
    def test_rejects_invalid_manifest(self):
        self.files['manifest.webmanifest']=b'{"display":"standalone"}';self.repackage()
        with self.assertRaisesRegex(ValueError,'webmanifest'):runtime_preflight(self.package)
    def test_rejects_asset_path_escape(self):
        for value in ('../evil.js','//remote.test/app.js','https://example.org/app.js','/../escape','./.private','C:/file.js','assets/%2Fsecret'):
            with self.subTest(value=value),self.assertRaises(ValueError):resolve_reference(value)
    def test_http_local_editor_serves_real_html_and_js(self):
        _,server,base=self.serve()
        with self.get(base+'/') as response:
            self.assertEqual(response.status,200)
            self.assertEqual(response.read(),self.files['index.html'])
            self.assertEqual(response.headers['X-Content-Type-Options'],'nosniff')
            self.assertIn("script-src 'self'",response.headers['Content-Security-Policy'])
        with self.get(base+'/assets/app.js') as response:
            self.assertEqual(response.read(),self.files['assets/app.js'])
            self.assertEqual(response.headers['Cache-Control'],'no-store')
        self.assertEqual(server.server_address[0],'127.0.0.1')
    def test_http_health_contains_no_file_paths(self):
        _,_,base=self.serve()
        with self.get(base+'/__hydra/status') as response:
            report=json.loads(response.read())
        self.assertEqual(report['status'],'VERIFIED')
        self.assertFalse(report['nativeInstallerCertified'])
        self.assertNotIn(str(self.root),json.dumps(report))
    def test_http_spa_fallback_only_navigation(self):
        _,_,base=self.serve()
        with self.get(base+'/some/diagram',Accept='text/html') as response:
            self.assertEqual(response.read(),self.files['index.html'])
        with self.assertRaises(HTTPError) as err:self.get(base+'/api/items',Accept='text/html')
        self.assertEqual(err.exception.code,404)
    def test_http_missing_asset_not_html(self):
        _,_,base=self.serve()
        with self.assertRaises(HTTPError) as err:self.get(base+'/assets/missing.js',Accept='text/html')
        self.assertEqual(err.exception.code,404)
    def test_http_rejects_path_traversal(self):
        available={'index.html':'a'*64}
        for target in ('/../file','/%2e%2e/private','/%2Fetc','/%255cwindows','/.env','/a//b'):
            with self.subTest(target=target),self.assertRaises(ValueError):request_file(target,'text/html',available)
    def test_http_host_guard_blocks_dns_rebinding(self):
        _,server,base=self.serve()
        port=server.server_address[1]
        with self.assertRaises(HTTPError) as err:self.get(base+'/',Host='malicious.example:'+str(port))
        self.assertEqual(err.exception.code,400)
    def test_http_post_cannot_modify_editor(self):
        _,_,base=self.serve()
        with self.assertRaises(HTTPError) as err:
            urlopen(Request(base+'/',data=b'{}',method='POST'),timeout=3)
        self.assertEqual(err.exception.code,405)
    def test_http_head_is_read_only(self):
        _,_,base=self.serve()
        with urlopen(Request(base+'/index.html',method='HEAD'),timeout=3) as response:
            self.assertEqual(response.status,200)
            self.assertEqual(response.read(),b'')
    def test_http_blocks_private_release_metadata(self):
        _,_,base=self.serve()
        for name in ('hydra-deployment.json','hydra-release.sha256','release.json','release-signature.json'):
            with self.subTest(name=name),self.assertRaises(HTTPError) as err:self.get(base+'/'+name)
            self.assertEqual(err.exception.code,404)
    def test_http_detects_tampering_after_publishing(self):
        state,_,base=self.serve()
        (state['webRoot']/'assets/app.js').write_bytes(b'MODIFIED!')
        with self.assertRaises(HTTPError) as err:self.get(base+'/assets/app.js')
        self.assertEqual(err.exception.code,404)
    def test_http_runtime_rejects_modified_source_on_start(self):
        state=self.publish()
        (state['webRoot']/'index.html').write_bytes(b'MODIFIED!')
        with self.assertRaisesRegex(ValueError,'differs'):runtime(self.registry,'editor-example','stable')
    def test_server_only_accepts_valid_ports(self):
        state=self.publish()
        for port in (-1,65536,True):
            with self.subTest(port=port),self.assertRaises(ValueError):open_server(state,port)
    def test_only_lowercase_safe_product(self):
        with self.assertRaisesRegex(ValueError,'lowercase'):runtime(self.registry,'..','stable')

if __name__=='__main__':
    suite=unittest.defaultTestLoader.loadTestsFromModule(sys.modules[__name__])
    results=unittest.TextTestRunner(verbosity=2).run(suite)
    print('BD5_BLOCK=PASS' if results.wasSuccessful() else 'BD5_BLOCK=FAIL',
          'tests='+str(results.testsRun),'skipped='+str(len(results.skipped)))
    sys.exit(0 if results.wasSuccessful() else 1)
