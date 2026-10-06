from pathlib import Path
import json
ROOT=Path(__file__).resolve().parents[2];checks=[]
def check(name,ok):checks.append((name,bool(ok)));print(('PASS' if ok else 'FAIL')+' - '+name)
def text(rel):return (ROOT/rel).read_text(encoding='utf-8')
policy=json.loads(text('config/hydra-toolchain.json'));discovery=json.loads(text('config/toolchain-discovery-policy.json'));tool=text('scripts/release/verify_toolchain.py');launcher=text('scripts/release/qualify_rc1.ps1');compat=text('scripts/release/qualify_rc1_windows.ps1');pom=text('pom.xml');final=text('scripts/validation/verify_final_tree.py');notes=text('HYDRA_V4.32.107_RELEASE_NOTES.md')
check('bytecode target remains Java 24','<java.version>24</java.version>' in pom and '<release>${java.version}</release>' in pom)
check('toolchain minimum remains Java 24',policy.get('java')=='24')
check('qualification maximum is Java 25',policy.get('javaQualificationMax')=='25' and discovery['java']['qualificationFeatures']==[24,25])
check('toolchain verifies Java feature range','java_feature_in_range' in tool and 'javaQualificationMax' in tool)
check('launcher delegates accepted Java versions to discovery policy','discover_jdks.py' in launcher)
check('launcher discovers JDK from providers and PATH','ConvertFrom-Json' in launcher and 'selected.root' in launcher)
check('launcher propagates selected JDK','$env:HYDRA_JAVA_HOME = $jdk' in launcher and '$env:JAVA_HOME = $jdk' in launcher and '$env:PATH = "$jdkBin;$env:PATH"' in launcher)
check('legacy Jdk24Home argument remains an alias','Alias("Jdk24Home")' in launcher and 'Alias("Jdk24Home")' in compat)
check('compatibility launcher delegates to canonical launcher','qualify_rc1.ps1' in compat and '-JdkHome $JdkHome' in compat)
check('Fix110 registered in final tree','verify_r5c13_fix110_java24_25_qualification.py' in final)
check('Fix110 release notes present','Fix110' in notes and 'Java 25' in notes)
failed=[name for name,ok in checks if not ok];print(f'FIX110_JAVA24_25_QUALIFICATION={len(checks)-len(failed)}/{len(checks)} '+('PASS' if not failed else 'FAIL'));raise SystemExit(1 if failed else 0)
