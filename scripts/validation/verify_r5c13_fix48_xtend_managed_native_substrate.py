from pathlib import Path
import json, shutil, subprocess, tempfile, textwrap
ROOT=Path(__file__).resolve().parents[2]

def builder_version(text):
    marker = "VERSION = '"
    start = text.find(marker)
    if start < 0:
        return ()
    value = text[start + len(marker):].split("'", 1)[0]
    try:
        return tuple(int(part) for part in value.split('.'))
    except ValueError:
        return ()

checks=[]
def check(name, cond):
    if not cond: raise SystemExit(f'FAIL - {name}')
    print(f'PASS - {name}'); checks.append(name)

support=(ROOT/'src/main/java/com/example/editor/transformation/nativeexec/TransformationNativeRuntimeSupport.java').read_text()
adapter=(ROOT/'src/main/java/com/example/editor/transformation/export/XtendTransformationLanguageAdapter.java').read_text()
bridge=(ROOT/'toolchains/official/HydraOfficialBridge.java').read_text()
builder=(ROOT/'scripts/toolchains/build_official_transformation_bridges.py').read_text()
check('Xtend uses managed native capability', 'TransformationExportLanguage.XTEND' in support and 'nativeCodeM2mM2tCapability()' in support)
check('Xtend adapter emits native plans', 'extends NativeCodeTransformationLanguageAdapterSupport' in adapter)
check('official bridge has bounded JVM classpath backend', 'case "JVM_CLASSPATH"' in bridge and 'trustedClasspath' in bridge and 'execution.classpath.file' in bridge)
check('official package builder has advanced beyond retained 1.3.0 packages', builder_version(builder) >= (2, 0, 0))
current_version='.'.join(str(part) for part in builder_version(builder))
for profile in sorted((ROOT/'toolchains/official/profiles').glob('*.properties')):
    check(f'{profile.stem} current profile version aligned', f'bridge.version={current_version}' in profile.read_text())
java=shutil.which('java'); javac=shutil.which('javac'); jar=shutil.which('jar')
check('Java toolchain available for bridge harness', bool(java and javac and jar))
xtend=ROOT/'distribution/transformation-toolchains/xtend/hydra-official-bridge/1.3.0/payload'
check('retained Xtend 1.3.0 package keeps its historical version', 'bridge.version=1.3.0' in (xtend/'bridge.properties').read_text())
desc=json.loads(subprocess.check_output([java,str(xtend/'HydraOfficialBridge.java'),str(xtend/'bridge.properties'),'--hydra-describe'],text=True))
check('base Xtend package stays fail closed without third-party engine', desc['toolAvailable'] is False and desc['executionAvailable'] is False)
check('base Xtend package advertises exact families and directions', set(desc['supportedKinds'])=={'M2M','M2T'} and set(desc['supportedDirections'])=={'FORWARD','REVERSE'})
with tempfile.TemporaryDirectory(prefix='hydra-fix48-jvmcp-') as td:
    td=Path(td); bundle=td/'bundle'; (bundle/'engine').mkdir(parents=True)
    shutil.copy2(ROOT/'toolchains/official/HydraOfficialBridge.java',bundle/'HydraOfficialBridge.java')
    shutil.copy2(ROOT/'toolchains/official/profiles/xtend.properties',bundle/'bridge.properties')
    src=td/'src/hydra/xtend/runtime'; src.mkdir(parents=True)
    (src/'HydraXtendNativeRuntime.java').write_text(textwrap.dedent('''
        package hydra.xtend.runtime;
        import java.nio.file.*;
        public final class HydraXtendNativeRuntime {
          public static void main(String[] args) throws Exception {
            Path request=Path.of(args[0]).toAbsolutePath();
            Files.writeString(request.getParent().resolve("xtend-backend-ran.txt"), "ok");
          }
        }
    '''))
    classes=td/'classes'; classes.mkdir()
    subprocess.check_call([javac,'-d',str(classes),str(src/'HydraXtendNativeRuntime.java')])
    runtime=bundle/'engine/hydra-xtend-runtime.jar'
    subprocess.check_call([jar,'--create','--file',str(runtime),'-C',str(classes),'.'])
    (bundle/'engine/classpath.txt').write_text('engine/hydra-xtend-runtime.jar\n')
    workspace=td/'workspace'; workspace.mkdir(); request=workspace/'native-request.json'; request.write_text('{}')
    ready=json.loads(subprocess.check_output([java,str(bundle/'HydraOfficialBridge.java'),str(bundle/'bridge.properties'),'--hydra-describe'],text=True))
    check('complete trusted JVM payload becomes executable', ready['toolAvailable'] is True and ready['executionAvailable'] is True)
    subprocess.check_call([java,str(bundle/'HydraOfficialBridge.java'),str(bundle/'bridge.properties'),str(request)])
    check('JVM classpath backend launches the declared runtime main class', (workspace/'xtend-backend-ran.txt').read_text()=='ok')
print(f'FIX48_XTEND_MANAGED_NATIVE_SUBSTRATE={len(checks)}/{len(checks)} PASS')
