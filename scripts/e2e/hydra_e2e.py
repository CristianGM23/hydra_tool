#!/usr/bin/env python3
"""HYDRA E2E: real commands and observations; no synthetic completion.

This is a separate, additive toolkit; it never changes production HYDRA code.
A build PASS is LIMITED. A command+independent-oracle PASS is PARTIAL.
COMPLETE requires independently signed receipts accepted by Fix185's strict gate.
"""
from __future__ import annotations
import argparse
import csv
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

TOOL_ROOT = Path(__file__).resolve().parent
MATRIX_PATH = TOOL_ROOT / 'fix185_native_acceptance_matrix.json'
TARGETS = ('EMF','LANGIUM','SIRIUS_DESKTOP','ECLIPSE_GLSP','XTEXT','ANTLR',
           'BESSER_SDK','BESSER_WEB','WINDOWS','LINUX','MACOS','DISTRIBUTION')
PROFILES = {'EMF':'emf','LANGIUM':'langium','SIRIUS_DESKTOP':'sirius',
            'ECLIPSE_GLSP':'glsp','XTEXT':'xtext','ANTLR':'antlr',
            'BESSER_SDK':'besser','BESSER_WEB':'besser'}
ALLOWED_RUNNERS = {'python','python3','py','node','npm','npm.cmd','mvn','mvn.cmd',
                   'cargo','npx','npx.cmd','java','javac','pytest','xvfb-run',
                   './mvnw','./mvnw.cmd','pnpm','pnpm.cmd','gradle','gradle.bat','./gradlew','./gradlew.bat'}
OS_TARGETS = {'WINDOWS','LINUX','MACOS'}
P_BY_SCENARIO={'SOURCE_PROVENANCE':'P25-P26','BUILD_OFFICIAL':'P33-P36','STRUCTURE':'P04-P06',
'VALIDATION':'P13-P16','QUICK_FIX':'P13-P16','EVENTS':'P17-P18','GAMIFICATION':'P19-P20',
'PERSISTENCE':'P05-P06','TRANSFORMATIONS':'P21-P24','INSTALL':'P33-P36',
'GUI':'P07-P09','UPDATER_SIGNATURE':'P33-P39','DATA_MIGRATION':'P31-P32',
'GENERATED_PROJECT':'P25-P26','RUNTIME':'P07-P09','GRAPH_EDITOR':'P07-P09',
'ROUNDTRIP':'P27-P28','EVENTS_GAMIFICATION':'P17-P20',
'OFFICIAL_RUNTIME':'P07-P12','RELEASE_GATE':'P37-P39',
'TAMPERED_UPDATE':'P37-P39','CRASH_CONCURRENCY':'P31-P39','ACCURATE_STATUS':'P37-P40'}
LAYER_BY_SCENARIO={'SOURCE_PROVENANCE':'Proyecto y exportación','BUILD_OFFICIAL':'Integración nativa',
'STRUCTURE':'Metamodelo y semántica','VALIDATION':'Validación','QUICK_FIX':'Correcciones',
'EVENTS':'Eventos','GAMIFICATION':'Gamificación','PERSISTENCE':'Persistencia',
'TRANSFORMATIONS':'Transformaciones','INSTALL':'Distribución','GUI':'Interfaz',
'UPDATER_SIGNATURE':'Seguridad de actualización','DATA_MIGRATION':'Persistencia',
'GENERATED_PROJECT':'Exportación','RUNTIME':'Integración nativa',
'GRAPH_EDITOR':'Editor gráfico','ROUNDTRIP':'Intercambio y recuperación',
'EVENTS_GAMIFICATION':'Eventos y gamificación','OFFICIAL_RUNTIME':'Integración nativa',
'RELEASE_GATE':'Calidad y publicación','TAMPERED_UPDATE':'Seguridad',
'CRASH_CONCURRENCY':'Resiliencia','ACCURATE_STATUS':'Observabilidad'}


def sha(path:Path)->str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_json(path:Path):
    if path.is_symlink() or not path.is_file() or path.stat().st_size > 1024*1024:
        raise ValueError('Missing, linked or oversized input: '+str(path))
    return json.loads(path.read_text(encoding='utf-8'))


def save_json(path:Path,data):
    if path.is_symlink():raise ValueError('Refusing symbolic output: '+str(path))
    path.parent.mkdir(parents=True,exist_ok=True)
    temp=path.with_name(path.name+'.tmp')
    if temp.exists() or temp.is_symlink():raise ValueError('Output staging already exists')
    try:
        temp.write_text(json.dumps(data,indent=2,ensure_ascii=False,sort_keys=True)+'\n',encoding='utf-8')
        os.replace(temp,path)
    finally:
        if temp.exists():temp.unlink()


def matrix():
    c=read_json(MATRIX_PATH)['cases']
    if len(c)!=63 or {x['target'] for x in c}!=set(TARGETS) or len({x['id'] for x in c})!=63:
        raise ValueError('HYDRA matrix is not the canonical 63-case portfolio')
    if any(c[i]['id']!=f'NATIVE-{i+1:03d}' for i in range(63)):
        raise ValueError('Canonical case sequence changed')
    return c


def config(root:Path,path:Path):
    d=read_json(path)
    if d.get('schemaVersion')!=1 or 'targets' not in d or set(d['targets'])!=set(TARGETS):
        raise ValueError('Config must declare all 12 official targets')
    if any(not isinstance(d['targets'][t],dict) for t in TARGETS):
        raise ValueError('Invalid target config')
    return d


def within(root:Path,rel:str,*,file:bool=False)->Path:
    if not isinstance(rel,str) or not rel or '\\' in rel or rel.startswith('/') or ':' in rel or len(rel)>255:
        raise ValueError('Unsafe relative path: '+str(rel))
    parts=rel.split('/')
    if any(p in ('','..','.') for p in parts):raise ValueError('Unsafe relative path')
    base=root.resolve()
    p=base
    for part in parts:
        p=p/part
        if p.is_symlink():raise ValueError('Symlinks not permitted in E2E paths: '+rel)
    if not p.resolve().is_relative_to(base):raise ValueError('E2E path escapes project')
    if file and not p.is_file():raise ValueError('E2E file missing: '+rel)
    return p


def command(root:Path,project:Path,argv:list[str],observed:Path|None,timeout:int)->dict:
    if not isinstance(argv,list) or not argv or any(not isinstance(v,str) or not v or len(v)>900 for v in argv):
        raise ValueError('Command must be a reviewed non-empty argv array')
    exe=argv[0].lower()
    if exe not in ALLOWED_RUNNERS:raise ValueError('Unapproved executable '+exe)
    if exe.startswith('./') and not (project/argv[0]).is_file():raise ValueError('Wrapper not found')
    env=os.environ.copy()
    env['HYDRA_E2E_ROOT']=str(root.resolve())
    if observed:
        env['HYDRA_E2E_OBSERVED_PATH']=str(observed)
        env['HYDRA_E2E_OBSERVED_ONLY']='1'
    start=time.monotonic()
    try:
        p=subprocess.run(argv,cwd=project,env=env,shell=False,
                         stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,
                         errors='replace',timeout=timeout)
        return {'status':'PASSED' if p.returncode==0 else 'FAILED','exitCode':p.returncode,
                'durationSeconds':round(time.monotonic()-start,3),
                'argv':argv,'outputTail':p.stdout[-10000:]}
    except subprocess.TimeoutExpired as e:
        return {'status':'TIMED_OUT','durationSeconds':round(time.monotonic()-start,3),'argv':argv}
    except OSError as e:
        return {'status':'TOOL_UNAVAILABLE' if isinstance(e,FileNotFoundError) else 'FAILED',
                'error':str(e),'argv':argv}


def execute_target(root:Path,conf:dict,target:str,report_dir:Path,timeout:int)->dict:
    entry=conf['targets'][target]
    selected=[c for c in matrix() if c['target']==target]
    report={'schemaVersion':1,'target':target,'timestamp':datetime.now(timezone.utc).isoformat(),
            'sourceManifestSha256':sha(root/'MANIFEST_SHA256.txt'),
            'sourceRoot':str(root.resolve()),'buildStatus':'NOT_EXECUTED','cases':[],
            'certification':'NOT_CERTIFIED','signedEvidence':False}
    rel=entry.get('project')
    if not rel:
        report['reason']='Project not configured; export a genuine project from HYDRA'
        report['cases']=[{'id':c['id'],'status':'NOT_EXECUTED','reason':'Missing generated project'} for c in selected]
        return report
    try:project=within(root,rel)
    except (OSError,ValueError) as e:
        report['reason']=str(e)
        report['cases']=[{'id':c['id'],'status':'BLOCKED','reason':str(e)} for c in selected]
        return report
    if not project.is_dir():
        report['reason']='Generated project directory missing: '+rel
        report['cases']=[{'id':c['id'],'status':'BLOCKED','reason':report['reason']} for c in selected]
        return report
    commands=entry.get('buildCommands')
    if not isinstance(commands,list) or not commands:
        report['reason']='No reviewed native build and test commands configured'
        report['cases']=[{'id':c['id'],'status':'BLOCKED','reason':report['reason']} for c in selected]
        return report
    build=[]
    try:
        for argv in commands:
            res=command(root,project,argv,None,timeout)
            build.append(res)
            if res['status']!='PASSED':break
    except ValueError as e:
        build.append({'status':'BLOCKED','error':str(e)})
    report['buildSteps']=build
    report['buildStatus']='PASSED' if len(build)==len(commands) and all(s['status']=='PASSED' for s in build) else 'FAILED'
    for case in selected:
        case_id=case['id']
        result={'id':case_id,'scenario':case['scenario'],'status':'NOT_EXECUTED'}
        if report['buildStatus']!='PASSED':
            result.update(status='BLOCKED',reason='Native build/test stage not passed')
        else:
            spec=entry.get('cases',{}).get(case_id)
            if not isinstance(spec,dict) or not spec.get('argv') or not spec.get('oracle'):
                result.update(status='BUILD_ONLY',reason='Real scenario driver and independent oracle required')
            else:
                try:
                    expected_file=within(root,spec['oracle'],file=True)
                    expected_hash_before=sha(expected_file)
                    result_path=report_dir/'observed'/(case_id+'.observed.json')
                    result_path.parent.mkdir(parents=True,exist_ok=True)
                    if result_path.exists() or result_path.is_symlink():
                        raise ValueError('Previously existing observed output: '+str(result_path))
                    cmd=command(root,project,spec['argv'],result_path,timeout)
                    result['process']=cmd
                    if cmd['status']!='PASSED':
                        result.update(status=cmd['status'],reason='Native probe did not pass')
                    elif not result_path.is_file() or result_path.is_symlink():
                        result.update(status='FAILED',reason='No native observations recorded')
                    else:
                        expected=read_json(expected_file)
                        observed=read_json(result_path)
                        if sha(expected_file)!=expected_hash_before:
                            result.update(status='FAILED',reason='Oracle mutated during test')
                        elif not isinstance(observed,dict) or not isinstance(observed.get('observations'),dict) or not observed['observations']:
                            result.update(status='FAILED',reason='Empty observations')
                        elif observed!=expected:
                            result.update(status='FAILED',reason='Native observations differ from independent oracle')
                        else:
                            result.update(status='ORACLE_MATCH_UNATTESTED',
                                          oracleSha256=expected_hash_before,observedSha256=sha(result_path))
                except (OSError,ValueError,TypeError,json.JSONDecodeError) as e:
                    result.update(status='FAILED',reason=str(e))
        report['cases'].append(result)
    report['scenariosMatched']=sum(x['status']=='ORACLE_MATCH_UNATTESTED' for x in report['cases'])
    report['scenariosRequired']=len(selected)
    report['status']='ORACLE_MATCH_UNATTESTED' if report['scenariosMatched']==len(selected) else 'INCOMPLETE'
    return report


def trusted_gate_case_ids(result:dict|None)->set[str]:
    """Only a successful independent Fix185 gate can elevate a case to COMPLETE.

    Reports written by arbitrary scenario drivers or by a failed gate are never
    credentials. The caller must additionally check the gate process exit code.
    """
    if not isinstance(result,dict):return set()
    specs={c['id'] for c in matrix()}
    outcomes=result.get('results')
    if (result.get('status')!='COMPLETE' or result.get('nativeLineComplete') is not True
        or result.get('casesRequired')!=63 or result.get('casesPassed')!=63
        or result.get('casesBlocked')!=0 or not isinstance(outcomes,list)
        or len(outcomes)!=63):return set()
    ids=[r.get('caseId') for r in outcomes if isinstance(r,dict)
         and r.get('status')=='ATTESTED_ORACLE_MATCH']
    if len(ids)!=63 or set(ids)!=specs:return set()
    if len(set(ids))!=63:return set()
    return set(ids)


def evidence_sources(root:Path)->dict:
    """Discover five official inputs; existence is not certification."""
    fields={
      'HYDRA_RELEASE_PACKAGE':('file','package'),
      'HYDRA_NATIVE_EVIDENCE_DIR':('directory','evidence'),
      'HYDRA_NATIVE_TRUST_POLICY':('file','trust'),
      'HYDRA_NATIVE_ORACLE_DIR':('directory','oracle'),
      'HYDRA_NATIVE_OBSERVED_DIR':('directory','observed'),
    }
    checks=[]; paths={}
    for name,(kind,key) in fields.items():
        raw=os.environ.get(name,'').strip()
        if not raw:
            checks.append({'environment':name,'state':'MISSING','detail':'Variable not configured'})
            continue
        path=Path(raw)
        if not path.is_absolute():path=root/path
        if path.is_symlink():
            checks.append({'environment':name,'state':'UNSAFE_LINK','detail':'Symbolic input path'})
            continue
        present=path.is_file() if kind=='file' else path.is_dir()
        if not present:
            checks.append({'environment':name,'state':'NOT_FOUND','detail':str(path)})
            continue
        if kind=='file' and path.stat().st_size==0:
            checks.append({'environment':name,'state':'EMPTY','detail':str(path)})
            continue
        paths[key]=path.resolve()
        checks.append({'environment':name,'state':'PRESENT_UNVERIFIED','detail':str(path)})
    ready=len(paths)==5
    return {'schemaVersion':1,'status':'READY_FOR_OFFICIAL_VERIFICATION' if ready else 'BLOCKED',
            'nativeCertified':False,'missingOrInvalid':[v for v in checks if v['state']!='PRESENT_UNVERIFIED'],
            'inputs':checks,'paths':paths}


def evidence_preflight(args):
    result=evidence_sources(args.root.resolve())
    payload={k:v for k,v in result.items() if k!='paths'}
    if args.output:save_json(args.output,payload)
    print('HYDRA_NATIVE_EVIDENCE_PREFLIGHT='+result['status'])
    for item in result['missingOrInvalid']:
        print('  '+item['environment']+' '+item['state']+' '+item['detail'])
    if result['status']=='READY_FOR_OFFICIAL_VERIFICATION':
        print('  Inputs exist; signatures and 63 scenarios still require strict Fix185 verification.')
    return 2 if args.require_ready and result['status']!='READY_FOR_OFFICIAL_VERIFICATION' else 0


def aggregate(root:Path,report_dir:Path,strict_results:dict|None=None):
    src=sha(root/'MANIFEST_SHA256.txt')
    specs=matrix()
    by_id={}
    issues=[]
    for target in TARGETS:
        path=report_dir/(target.lower()+'.json')
        if not path.is_file():continue
        try:
            report=read_json(path)
            if report.get('target')!=target or report.get('sourceManifestSha256')!=src:
                issues.append(target+': stale/mismatched source');continue
            for c in report.get('cases',[]):
                if c.get('id') not in {s['id'] for s in specs if s['target']==target}:
                    issues.append(target+': unknown case');continue
                by_id[c['id']]=c
        except (ValueError,OSError,TypeError,json.JSONDecodeError) as e:
            issues.append(target+': '+str(e))
    strictly=trusted_gate_case_ids(strict_results)
    rows=[]
    for case in specs:
        result=by_id.get(case['id'],{})
        status=result.get('status','NOT_EXECUTED')
        depth=('COMPLETE' if case['id'] in strictly
               else 'PARTIAL' if status=='ORACLE_MATCH_UNATTESTED'
               else 'LIMITED' if status in ('PASSED','BUILD_ONLY')
               else 'NOT_EXECUTED' if status=='NOT_EXECUTED'
               else 'BLOCKED')
        rows.append({'id':case['id'],'target':case['target'],'scenario':case['scenario'],
                     'block':case['process'],'functionalProcess':P_BY_SCENARIO.get(case['scenario'],'P37-P39'),
                     'layer':LAYER_BY_SCENARIO.get(case['scenario'],'Integración'),
                     'execution':status,'coverage':depth,
                     'evidenceType':('TRUSTED_SIGNED_OFFICIAL_RUNNER' if depth=='COMPLETE' else
                                     'OBSERVATION_WITH_ORACLE_UNTRUSTED' if depth=='PARTIAL' else 'NONE'),
                     'certified':depth=='COMPLETE'})
    counts={name:sum(r['coverage']==name for r in rows) for name in ('COMPLETE','PARTIAL','LIMITED','BLOCKED','NOT_EXECUTED')}
    return {'schemaVersion':1,'project':'HYDRA','specification':'B10–B13 / D10–D13',
            'sourceManifestSha256':src,'nativeCertification':'COMPLETE' if len(strictly)==63 else 'BLOCKED',
            'casesRequired':63,'casesComplete':counts['COMPLETE'],'coverageCounts':counts,
            'issues':issues,'cases':rows,
            'warning':'Build and unsigned oracle matches never certify native E2E. Run Fix185 strict gate with real signed runner receipts.'}


def coverage(args):
    root=args.root.resolve()
    verified=None
    strict_issue=None
    opts=[args.strict_package,args.strict_evidence_dir,args.strict_trust_policy,args.strict_oracle_root,args.strict_observed_root]
    if any(opts):
        if not all(opts):
            strict_issue='Five official strict-gate inputs must be supplied together'
        else:
            import tempfile
            with tempfile.TemporaryDirectory(prefix='hydra-e2e-strict-') as tmp:
                path=Path(tmp)/'result.json'
                gate=root/'scripts/distribution/fix185_complete_line_gate.py'
                try:
                    proc=subprocess.run([sys.executable,str(gate),'--package',str(args.strict_package),
                        '--canonical-manifest',str(root/'MANIFEST_SHA256.txt'),
                        '--evidence-dir',str(args.strict_evidence_dir),
                        '--trust-policy',str(args.strict_trust_policy),
                        '--oracle-root',str(args.strict_oracle_root),
                        '--observed-root',str(args.strict_observed_root),'--report',str(path)],
                        cwd=root,capture_output=True,text=True,timeout=150)
                    if proc.returncode!=0:
                        strict_issue='Official Fix185 gate rejected inputs (exit='+str(proc.returncode)+'): '+(proc.stdout+proc.stderr)[-800:]
                    elif not path.is_file():
                        strict_issue='Official Fix185 gate did not produce a report'
                    else:
                        candidate=read_json(path)
                        if len(trusted_gate_case_ids(candidate))==63:
                            verified=candidate
                        else:
                            strict_issue='Official Fix185 gate returned incomplete or inconsistent evidence'
                except (OSError,subprocess.TimeoutExpired,ValueError,TypeError,json.JSONDecodeError) as exc:
                    strict_issue='Official Fix185 gate failed: '+str(exc)
    report=aggregate(root,args.report_dir,verified)
    if strict_issue:report['issues'].append(strict_issue)
    save_json(args.output,report)
    args.csv.parent.mkdir(parents=True,exist_ok=True)
    with args.csv.open('w',encoding='utf-8-sig',newline='') as f:
        w=csv.DictWriter(f,fieldnames=['id','target','scenario','block','functionalProcess','layer','execution','coverage','certified'])
        w.writeheader()
        for row in report['cases']:w.writerow({k:row[k] for k in w.fieldnames})
    if args.archify:
        if args.archify.is_symlink():raise ValueError('Archify path is symbolic')
        save_json(args.archify,report)
    print(f"HYDRA_E2E_COVERAGE={report['nativeCertification']} complete={report['casesComplete']}/63 counts={report['coverageCounts']}")
    if strict_issue:print('HYDRA_E2E_STRICT_GATE=BLOCKED '+strict_issue[-300:])
    return 2 if args.require_complete and report['nativeCertification']!='COMPLETE' else 0


def certify(args):
    """CI release stage: never claim COMPLETE if inputs are absent or invalid."""
    state=evidence_sources(args.root.resolve())
    args.strict_package=state['paths'].get('package')
    args.strict_evidence_dir=state['paths'].get('evidence')
    args.strict_trust_policy=state['paths'].get('trust')
    args.strict_oracle_root=state['paths'].get('oracle')
    args.strict_observed_root=state['paths'].get('observed')
    args.require_complete=True
    status=coverage(args)
    if state['status']!='READY_FOR_OFFICIAL_VERIFICATION':
        report=read_json(args.output)
        report['issues'].extend(v['environment']+': '+v['state'] for v in state['missingOrInvalid'])
        report['evidencePreflight']='BLOCKED'
        save_json(args.output,report)
        for item in state['missingOrInvalid']:
            print('HYDRA_NATIVE_EVIDENCE_MISSING '+item['environment']+' '+item['state'])
        return 2
    return status


def preflight(args):
    root=args.root.resolve(); conf=config(root,args.config);rows=[]
    for target in TARGETS:
        entry=conf['targets'][target]
        project=entry.get('project')
        p=(within(root,project) if project else None)
        cs=[c for c in matrix() if c['target']==target]
        configured=sum(bool(entry.get('cases',{}).get(c['id'],{}).get('argv') and
                            entry.get('cases',{}).get(c['id'],{}).get('oracle')) for c in cs)
        rows.append({'target':target,'generatedProject':project,'projectExists':bool(p and p.is_dir()),
                     'buildConfigured':bool(entry.get('buildCommands')),
                     'scenariosConfigured':configured,'scenariosRequired':len(cs),
                     'readyToExecute':bool(p and p.is_dir() and entry.get('buildCommands') and configured==len(cs))})
    payload={'schemaVersion':1,'status':'READY_FOR_EXECUTION' if all(x['readyToExecute'] for x in rows) else 'BLOCKED',
             'sourceManifestSha256':sha(root/'MANIFEST_SHA256.txt'),'targets':rows}
    if args.output:save_json(args.output,payload)
    for r in rows:print(f"{r['target']:17} project={str(r['projectExists']):5} build={str(r['buildConfigured']):5} cases={r['scenariosConfigured']}/{r['scenariosRequired']}")
    return 0 if payload['status']=='READY_FOR_EXECUTION' else 2


def main():
    a=argparse.ArgumentParser(description=__doc__)
    sub=a.add_subparsers(dest='command',required=True)
    p=sub.add_parser('preflight');p.add_argument('--root',type=Path,required=True);p.add_argument('--config',type=Path,required=True);p.add_argument('--output',type=Path)
    p=sub.add_parser('evidence-preflight');p.add_argument('--root',type=Path,required=True);p.add_argument('--output',type=Path);p.add_argument('--require-ready',action='store_true')
    p=sub.add_parser('run-target');p.add_argument('--root',type=Path,required=True);p.add_argument('--config',type=Path,required=True)
    p.add_argument('--target',choices=TARGETS,required=True);p.add_argument('--report-dir',type=Path,required=True)
    p.add_argument('--timeout-seconds',type=int,default=1800)
    p=sub.add_parser('coverage');p.add_argument('--root',type=Path,required=True);p.add_argument('--report-dir',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True);p.add_argument('--csv',type=Path,required=True);p.add_argument('--archify',type=Path)
    for flag in ('strict-package','strict-evidence-dir','strict-trust-policy','strict-oracle-root','strict-observed-root'):
        p.add_argument('--'+flag,type=Path)
    p.add_argument('--require-complete',action='store_true')
    p=sub.add_parser('certify');p.add_argument('--root',type=Path,required=True);p.add_argument('--report-dir',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True);p.add_argument('--csv',type=Path,required=True);p.add_argument('--archify',type=Path)
    args=a.parse_args()
    try:
        if args.command=='preflight':return preflight(args)
        if args.command=='evidence-preflight':return evidence_preflight(args)
        if args.command=='coverage':return coverage(args)
        if args.command=='certify':return certify(args)
        if not 10<=args.timeout_seconds<=3600:raise ValueError('Timeout out of range')
        root=args.root.resolve();conf=config(root,args.config)
        report=execute_target(root,conf,args.target,args.report_dir,args.timeout_seconds)
        save_json(args.report_dir/(args.target.lower()+'.json'),report)
        print('HYDRA_E2E_'+args.target+'='+report.get('status',report['certification'])+
              ' matched='+str(report.get('scenariosMatched',0))+'/'+str(len(report['cases'])))
        return 0 if report.get('status')=='ORACLE_MATCH_UNATTESTED' else 2
    except (ValueError,OSError,KeyError,TypeError,json.JSONDecodeError) as e:
        print('HYDRA_E2E=FAIL '+str(e),file=sys.stderr);return 2

if __name__=='__main__':raise SystemExit(main())
