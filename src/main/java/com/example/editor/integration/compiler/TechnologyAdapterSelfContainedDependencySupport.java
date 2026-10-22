package com.example.editor.integration.compiler;

import java.util.ArrayList;
import java.util.List;

import com.example.editor.integration.IntegrationArtifactKind;
import com.example.editor.grammar.core.GrammarTechnology;

/**
 * Generates deterministic build-time vendoring contracts so the final editor
 * artifact can be installed without contacting npm/PyPI/Maven/p2 repositories.
 * Host APIs (Eclipse/VS Code/Python/Node) are deliberately not duplicated.
 */
public final class TechnologyAdapterSelfContainedDependencySupport {
    /** Descripción: Inicializa una instancia de TechnologyAdapterSelfContainedDependencySupport con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private TechnologyAdapterSelfContainedDependencySupport() { }

    /** Descripción: Evalúa la condición «hasVisualEditor» sin modificar el estado externo. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ public static boolean hasVisualEditor(TechnologyCompilationRequest request) {
        return request.find(IntegrationArtifactKind.EDITOR_DEFINITION).isPresent();
    }

    /** Descripción: Ejecuta la operación «grammarTechnology» correspondiente a la responsabilidad de TechnologyAdapterSelfContainedDependencySupport. Entradas: request: solicitud con los datos necesarios para ejecutar la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: devuelve el resultado definido por el contrato del método. */ public static GrammarTechnology grammarTechnology(TechnologyCompilationRequest request,
            tools.jackson.databind.json.JsonMapper mapper) {
        return request.find(IntegrationArtifactKind.GRAMMAR).map(document -> {
            try {
                return mapper.readValue(document.content(), com.example.editor.grammar.core.GrammarDefinitionModel.class).technology();
            } catch (Exception ignored) {
                return null;
            }
        }).orElse(null);
    }

    /** Descripción: Ejecuta la operación «npmVendorFiles» correspondiente a la responsabilidad de TechnologyAdapterSelfContainedDependencySupport. Entradas: root: ruta o ubicación utilizada por la operación; packageLabel: dato de entrada necesario para la operación; runtimePackages: dato de entrada necesario para la operación; sourceIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static List<TechnologyGeneratedFile> npmVendorFiles(String root, String packageLabel,
            List<String> runtimePackages, List<String> sourceIds) {
        List<TechnologyGeneratedFile> files = new ArrayList<>();
        List<String> distinct = runtimePackages.stream().map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
        String packages = distinct.stream().map(value -> "\"" + json(value) + "\"").reduce((a,b) -> a + "," + b).orElse("");
        files.add(TechnologyAdapterSupport.text(root + "/offline/dependency-bundle.json", "application/json", """
                {"schemaVersion":"1.1","kind":"NPM_VENDOR_BUNDLE","package":"%s","installNetworkRequired":false,
                 "buildNetworkRequired":true,"hostProvided":["node","npm"],"runtimePackages":[%s],
                 "transitiveCache":"npm-cache","auditTarballs":"npm","vendorIndex":"vendor-index.json",
                 "order":["detect-compatible-host-packages","reuse-compatible","install-only-missing-from-transitive-cache","verify-exact-specs"]}
                """.formatted(json(packageLabel), packages), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/offline/prepare-vendor.mjs", "text/javascript", npmPrepare(distinct), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/offline/install-from-vendor.mjs", "text/javascript", npmInstall(distinct), sourceIds));
        return files;
    }

    /** Descripción: Ejecuta la operación «pythonWheelhouseFiles» correspondiente a la responsabilidad de TechnologyAdapterSelfContainedDependencySupport. Entradas: root: ruta o ubicación utilizada por la operación; packageLabel: dato de entrada necesario para la operación; requirements: dato de entrada necesario para la operación; sourceIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static List<TechnologyGeneratedFile> pythonWheelhouseFiles(String root, String packageLabel,
            List<String> requirements, List<String> sourceIds) {
        List<TechnologyGeneratedFile> files = new ArrayList<>();
        String requirementsText = String.join("\n", requirements.stream().map(String::trim).filter(value -> !value.isBlank()).distinct().toList()) + "\n";
        files.add(TechnologyAdapterSupport.text(root + "/offline/requirements-runtime.txt", "text/plain", requirementsText, sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/offline/dependency-bundle.json", "application/json", """
                {"schemaVersion":"1.1","kind":"PYTHON_WHEELHOUSE","package":"%s","installNetworkRequired":false,
                 "buildNetworkRequired":true,"hostProvided":["python>=3.10,<3.13"],"wheelhouse":"wheelhouse",
                 "integrityManifest":"wheelhouse-manifest.json",
                 "order":["create-venv","detect-compatible-installed","install-only-missing-from-local-wheelhouse","pip-check","verify"]}
                """.formatted(json(packageLabel)), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/offline/prepare-wheelhouse.py", "text/x-python", pythonPrepare(), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/offline/install-offline.py", "text/x-python", pythonInstall(), sourceIds));
        return files;
    }

    /** Descripción: Ejecuta la operación «eclipseP2VendorFiles» correspondiente a la responsabilidad de TechnologyAdapterSelfContainedDependencySupport. Entradas: root: ruta o ubicación utilizada por la operación; packageLabel: dato de entrada necesario para la operación; repository: dato de entrada necesario para la operación; installableUnits: dato de entrada necesario para la operación; sourceIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static List<TechnologyGeneratedFile> eclipseP2VendorFiles(String root, String packageLabel,
            String repository, List<String> installableUnits, List<String> sourceIds) {
        List<TechnologyGeneratedFile> files = new ArrayList<>();
        List<String> distinct = installableUnits.stream().map(String::trim).filter(value -> !value.isBlank()).distinct()
                .peek(TechnologyAdapterSelfContainedDependencySupport::requireSafeP2Unit).toList();
        String units = distinct.stream().map(value -> "\"" + json(value) + "\"").reduce((a,b) -> a + "," + b).orElse("");
        files.add(TechnologyAdapterSupport.text(root + "/offline/dependency-bundle.json", "application/json", """
                {"schemaVersion":"1.1","kind":"ECLIPSE_P2_VENDOR_REPOSITORY","package":"%s","installNetworkRequired":false,
                 "buildNetworkRequired":true,"sourceRepository":"%s","hostProvided":["equinox","eclipse-platform"],
                 "installableUnits":[%s],"localRepository":"p2",
                 "order":["detect-installed-ius","detect-installed-roots","reuse-compatible-installed","install-missing-from-local-p2","install-only-missing-from-local-p2","verify-installed-roots","restart"]}
                """.formatted(json(packageLabel), json(repository), units), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/offline/prepare-p2-mirror.xml", "application/xml",
                p2MirrorAnt(repository, distinct), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/offline/prepare-p2-mirror.sh", "text/x-shellscript",
                p2PrepareShell(), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/offline/install-offline.sh", "text/x-shellscript",
                p2InstallShell(distinct), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/offline/install-offline.ps1", "text/plain",
                p2InstallPowerShell(distinct), sourceIds));
        return files;
    }

    /** Descripción: Ejecuta la operación «mavenRuntimeVendorFiles» correspondiente a la responsabilidad de TechnologyAdapterSelfContainedDependencySupport. Entradas: root: ruta o ubicación utilizada por la operación; packageLabel: dato de entrada necesario para la operación; coordinates: dato de entrada necesario para la operación; sourceIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static List<TechnologyGeneratedFile> mavenRuntimeVendorFiles(String root, String packageLabel,
            List<String> coordinates, List<String> sourceIds) {
        List<TechnologyGeneratedFile> files = new ArrayList<>();
        List<String> distinct = coordinates.stream().map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
        String coordinatesJson = distinct.stream().map(value -> "\"" + json(value) + "\"")
                .reduce((a,b) -> a + "," + b).orElse("");
        files.add(TechnologyAdapterSupport.text(root + "/offline/maven-runtime-bundle.json", "application/json", """
                {"schemaVersion":"1.1","kind":"MAVEN_RUNTIME_LIBS","package":"%s","installNetworkRequired":false,
                 "buildNetworkRequired":true,"coordinates":[%s],"output":"offline/lib","integrityManifest":"offline/maven-runtime-manifest.json",
                 "order":["resolve-build-time-transitives","copy-runtime-jars","hash-vendor","verify-offline-runtime"]}
                """.formatted(json(packageLabel), coordinatesJson), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/offline/prepare-maven-runtime.py", "text/x-python",
                mavenPrepare(distinct), sourceIds));
        files.add(TechnologyAdapterSupport.text(root + "/offline/verify-maven-runtime.py", "text/x-python",
                mavenVerify(), sourceIds));
        return files;
    }

    /** Descripción: Ejecuta la operación «npmPrepare» correspondiente a la responsabilidad de TechnologyAdapterSelfContainedDependencySupport. Entradas: packages: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String npmPrepare(List<String> packages) {
        String array = packages.stream().map(value -> "'" + js(value) + "'").reduce((a,b)->a+","+b).orElse("");
        return """
                import {existsSync,mkdirSync,readdirSync,rmSync,writeFileSync} from 'node:fs';
                import {spawnSync} from 'node:child_process';
                import {dirname,join} from 'node:path'; import {fileURLToPath} from 'node:url';
                // Execute npm as a JavaScript CLI when possible. On Windows, npm.cmd
                // cannot be executed portably through spawnSync without invoking a shell.
                // Avoid a shell entirely so package specs cannot become shell commands.
                const npmCliCandidates=[process.env.npm_execpath,
                  join(dirname(process.execPath),'node_modules','npm','bin','npm-cli.js'),
                  join(dirname(process.execPath),'..','lib','node_modules','npm','bin','npm-cli.js')];
                const npmCli=npmCliCandidates.find(p=>p&&p.replaceAll(String.fromCharCode(92),'/').endsWith('/npm-cli.js')&&existsSync(p));
                const runNpm=(args,options={})=>{
                  if(npmCli)return spawnSync(process.execPath,[npmCli,...args],options);
                  if(process.platform==='win32')throw new Error('HYDRA_NPM_CLI_NOT_FOUND: npm-cli.js not found next to Node or in npm_execpath');
                  return spawnSync('npm',args,options);
                };
                const specs=[%s],here=dirname(fileURLToPath(import.meta.url)),tarballs=join(here,'npm'),cache=join(here,'npm-cache'),work=join(here,'.vendor-work');
                rmSync(work,{recursive:true,force:true});mkdirSync(work,{recursive:true});mkdirSync(tarballs,{recursive:true});mkdirSync(cache,{recursive:true});
                writeFileSync(join(work,'package.json'),JSON.stringify({private:true,name:'hydra-offline-vendor',version:'1.0.0'},null,2));
                if(specs.length){const r=runNpm(['install','--prefix',work,'--cache',cache,'--ignore-scripts','--no-audit','--no-fund','--package-lock=true',...specs],{stdio:'inherit'});if(r.status!==0)process.exit(r.status??1);}
                for(const spec of specs){const r=runNpm(['pack',spec,'--pack-destination',tarballs,'--ignore-scripts'],{stdio:'inherit'});if(r.status!==0)process.exit(r.status??1);}
                const packed=readdirSync(tarballs).filter(name=>name.endsWith('.tgz')).sort();
                if(specs.length&&!existsSync(join(cache,'_cacache')))throw new Error('npm transitive cache was not created');
                writeFileSync(join(here,'vendor-index.json'),JSON.stringify({schemaVersion:'1.0',specs,auditTarballs:packed,transitiveCache:'npm-cache'},null,2)+'\\n');
                rmSync(work,{recursive:true,force:true});console.log(`HYDRA_NPM_VENDOR_READY roots=${specs.length} tarballs=${packed.length}`);
                """.formatted(array);
    }

    /** Descripción: Ejecuta la operación «npmInstall» correspondiente a la responsabilidad de TechnologyAdapterSelfContainedDependencySupport. Entradas: packages: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String npmInstall(List<String> packages) {
        String specs = packages.stream().map(value -> "'" + js(value) + "'").reduce((a,b)->a+","+b).orElse("");
        return """
                import {existsSync} from 'node:fs'; import {spawnSync} from 'node:child_process';
                import {dirname,join} from 'node:path'; import {fileURLToPath} from 'node:url';
                // Execute npm as a JavaScript CLI when possible. On Windows, npm.cmd
                // cannot be executed portably through spawnSync without invoking a shell.
                // Avoid a shell entirely so package specs cannot become shell commands.
                const npmCliCandidates=[process.env.npm_execpath,
                  join(dirname(process.execPath),'node_modules','npm','bin','npm-cli.js'),
                  join(dirname(process.execPath),'..','lib','node_modules','npm','bin','npm-cli.js')];
                const npmCli=npmCliCandidates.find(p=>p&&p.replaceAll(String.fromCharCode(92),'/').endsWith('/npm-cli.js')&&existsSync(p));
                const runNpm=(args,options={})=>{
                  if(npmCli)return spawnSync(process.execPath,[npmCli,...args],options);
                  if(process.platform==='win32')throw new Error('HYDRA_NPM_CLI_NOT_FOUND: npm-cli.js not found next to Node or in npm_execpath');
                  return spawnSync('npm',args,options);
                };
                const required=[%s],here=dirname(fileURLToPath(import.meta.url)),cache=join(here,'npm-cache');
                const compatible=(spec)=>runNpm(['ls',spec,'--depth=0','--json'],{encoding:'utf8'}).status===0;
                const missing=required.filter(spec=>!compatible(spec));
                if(missing.length){if(!existsSync(join(cache,'_cacache')))throw new Error('HYDRA npm vendor cache is missing or incomplete: '+cache);const r=runNpm(['install','--offline','--cache',cache,'--ignore-scripts','--no-audit','--no-fund',...missing],{stdio:'inherit'});if(r.status!==0)process.exit(r.status??1);}
                const unresolved=required.filter(spec=>!compatible(spec));if(unresolved.length)throw new Error('HYDRA npm offline verification failed: '+unresolved.join(', '));
                console.log(`HYDRA_NPM_OFFLINE_INSTALL missing=${missing.length} reused=${required.length-missing.length} verified=${required.length}`);
                """.formatted(specs);
    }

    /** Descripción: Ejecuta la operación «pythonPrepare» correspondiente a la responsabilidad de TechnologyAdapterSelfContainedDependencySupport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static String pythonPrepare() {
        return """
                from pathlib import Path
                import hashlib,json,subprocess,sys
                root=Path(__file__).resolve().parent
                wheelhouse=root/'wheelhouse'; wheelhouse.mkdir(parents=True,exist_ok=True)
                subprocess.check_call([sys.executable,'-m','pip','wheel','--wheel-dir',str(wheelhouse),'-r',str(root/'requirements-runtime.txt')])
                wheels=sorted(wheelhouse.glob('*.whl'))
                if root.joinpath('requirements-runtime.txt').read_text(encoding='utf-8').strip() and not wheels:
                    raise SystemExit('HYDRA wheelhouse is empty')
                manifest={'schemaVersion':'1.0','wheels':[{'name':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'size':p.stat().st_size} for p in wheels]}
                root.joinpath('wheelhouse-manifest.json').write_text(json.dumps(manifest,indent=2)+'\\n',encoding='utf-8')
                print(f'HYDRA_WHEELHOUSE_READY wheels={len(wheels)}')
                """;
    }

    /** Descripción: Ejecuta la operación «pythonInstall» correspondiente a la responsabilidad de TechnologyAdapterSelfContainedDependencySupport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static String pythonInstall() {
        return """
                from pathlib import Path
                import hashlib,json,subprocess,sys
                root=Path(__file__).resolve().parent; wheelhouse=root/'wheelhouse'; requirements=root/'requirements-runtime.txt'; manifest_path=root/'wheelhouse-manifest.json'
                required=requirements.read_text(encoding='utf-8').strip()
                if required and (not wheelhouse.is_dir() or not any(wheelhouse.glob('*.whl'))):
                    raise SystemExit('HYDRA offline wheelhouse is missing or empty')
                if required and not manifest_path.is_file():
                    raise SystemExit('HYDRA wheelhouse integrity manifest is missing')
                if manifest_path.is_file():
                    manifest=json.loads(manifest_path.read_text(encoding='utf-8')); declared={entry['name']:entry for entry in manifest.get('wheels',[])}
                    actual={path.name:path for path in wheelhouse.glob('*.whl')} if wheelhouse.is_dir() else {}
                    if required and not declared: raise SystemExit('HYDRA wheelhouse integrity manifest is empty')
                    if set(actual)!=set(declared): raise SystemExit('HYDRA wheelhouse contents do not match integrity manifest')
                    for name,entry in declared.items():
                        path=actual[name]
                        if path.stat().st_size!=entry.get('size') or hashlib.sha256(path.read_bytes()).hexdigest()!=entry.get('sha256'):
                            raise SystemExit('Corrupt HYDRA wheel: '+name)
                subprocess.check_call([sys.executable,'-m','pip','install','--no-index','--find-links',str(wheelhouse),'--upgrade-strategy','only-if-needed','-r',str(requirements)])
                subprocess.check_call([sys.executable,'-m','pip','check'])
                print('HYDRA_WHEELHOUSE_OFFLINE_INSTALL=PASS')
                """;
    }

    /** Descripción: Ejecuta la operación «p2MirrorAnt» correspondiente a la responsabilidad de TechnologyAdapterSelfContainedDependencySupport. Entradas: repository: dato de entrada necesario para la operación; units: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String p2MirrorAnt(String repository, List<String> units) {
        StringBuilder ius=new StringBuilder();
        for(String unit:units) ius.append("    <iu id=\"").append(xml(unit)).append("\"/>\n");
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project name="hydra-offline-p2" default="mirror">
                  <target name="mirror">
                    <delete dir="${basedir}/p2" quiet="true"/><mkdir dir="${basedir}/p2"/>
                    <p2.mirror source="%s" destination="file:${basedir}/p2" verbose="true" validate="true">
                %s      <slicingOptions includeOptional="false" includeNonGreedy="false" includeFeatures="true" latestVersionOnly="true"/>
                    </p2.mirror>
                  </target>
                </project>
                """.formatted(xml(repository),ius);
    }

    /** Descripción: Ejecuta la operación «p2PrepareShell» correspondiente a la responsabilidad de TechnologyAdapterSelfContainedDependencySupport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static String p2PrepareShell() {
        return """
                #!/usr/bin/env sh
                set -eu
                : "${ECLIPSE_HOME:?Set ECLIPSE_HOME to an Eclipse installation containing p2 and the Ant runner}"
                HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
                "$ECLIPSE_HOME/eclipse" -nosplash -application org.eclipse.ant.core.antRunner -buildfile "$HERE/prepare-p2-mirror.xml"
                test -f "$HERE/p2/content.jar" -o -f "$HERE/p2/content.xml" || { echo 'HYDRA p2 mirror has no content metadata' >&2; exit 2; }
                echo HYDRA_P2_VENDOR_READY
                """;
    }

    /** Descripción: Ejecuta la operación «p2InstallShell» correspondiente a la responsabilidad de TechnologyAdapterSelfContainedDependencySupport. Entradas: units: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String p2InstallShell(List<String> units) {
        String required = String.join(" ", units);
        return """
                #!/usr/bin/env sh
                set -eu
                : "${ECLIPSE_HOME:?Set ECLIPSE_HOME}"
                HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd); ECLIPSE="$ECLIPSE_HOME/eclipse"; REQUIRED="%s"
                INSTALLED=$($ECLIPSE -nosplash -application org.eclipse.equinox.p2.director -listInstalledRoots 2>/dev/null || true)
                MISSING=''; REUSED=0; MISSING_COUNT=0
                for iu in $REQUIRED; do if printf '%%s\\n' "$INSTALLED" | grep -F "$iu" >/dev/null 2>&1; then REUSED=$((REUSED+1)); else MISSING="${MISSING}${MISSING:+,}$iu"; MISSING_COUNT=$((MISSING_COUNT+1)); fi; done
                if [ "$MISSING_COUNT" -gt 0 ]; then test -d "$HERE/p2" || { echo 'HYDRA local p2 repository is missing' >&2; exit 3; }; $ECLIPSE -nosplash -application org.eclipse.equinox.p2.director -repository "file:$HERE/p2" -installIU "$MISSING" -profileProperties org.eclipse.update.install.features=true; fi
                VERIFIED=$($ECLIPSE -nosplash -application org.eclipse.equinox.p2.director -listInstalledRoots 2>/dev/null || true)
                for iu in $REQUIRED; do printf '%%s\\n' "$VERIFIED" | grep -F "$iu" >/dev/null 2>&1 || { echo "HYDRA p2 verification failed for $iu" >&2; exit 4; }; done
                echo "HYDRA_P2_OFFLINE_INSTALL=PASS missing=$MISSING_COUNT reused=$REUSED"
                """.formatted(required.replace("\\", "\\\\").replace("\"", "\\\""));
    }

    /** Descripción: Ejecuta la operación «p2InstallPowerShell» correspondiente a la responsabilidad de TechnologyAdapterSelfContainedDependencySupport. Entradas: units: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String p2InstallPowerShell(List<String> units) {
        String required = units.stream().map(value -> "'" + value.replace("'", "''") + "'").reduce((a,b)->a+","+b).orElse("");
        return """
                $ErrorActionPreference='Stop'
                if (-not $env:ECLIPSE_HOME) { throw 'Set ECLIPSE_HOME' }
                $eclipse=Join-Path $env:ECLIPSE_HOME 'eclipse.exe'; $repoPath=Join-Path $PSScriptRoot 'p2'; $repo='file:'+$repoPath; $required=@(%s)
                $installed=& $eclipse -nosplash -application org.eclipse.equinox.p2.director -listInstalledRoots 2>$null
                $missing=@(); foreach($iu in $required){if(-not ($installed | Select-String -SimpleMatch $iu -Quiet)){$missing += $iu}}
                if($missing.Count -gt 0){if(-not (Test-Path $repoPath)){throw 'HYDRA local p2 repository is missing'};& $eclipse -nosplash -application org.eclipse.equinox.p2.director -repository $repo -installIU ($missing -join ',') -profileProperties org.eclipse.update.install.features=true;if($LASTEXITCODE -ne 0){exit $LASTEXITCODE}}
                $verified=& $eclipse -nosplash -application org.eclipse.equinox.p2.director -listInstalledRoots 2>$null
                foreach($iu in $required){if(-not ($verified | Select-String -SimpleMatch $iu -Quiet)){throw "HYDRA p2 verification failed for $iu"}}
                Write-Output ("HYDRA_P2_OFFLINE_INSTALL=PASS missing={0} reused={1}" -f $missing.Count,($required.Count-$missing.Count))
                """.formatted(required);
    }

    /** Descripción: Ejecuta la operación «mavenPrepare» correspondiente a la responsabilidad de TechnologyAdapterSelfContainedDependencySupport. Entradas: coordinates: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String mavenPrepare(List<String> coordinates) {
        String array = coordinates.stream().map(value -> "'" + py(value) + "'").reduce((a,b)->a+","+b).orElse("");
        return """
                from pathlib import Path
                import hashlib,json,shutil,subprocess,sys,tempfile
                coordinates=[%s]; root=Path(__file__).resolve().parent; lib=root/'lib'; lib.mkdir(parents=True,exist_ok=True)
                mvn=shutil.which('mvn') or shutil.which('mvn.cmd')
                if not mvn: raise SystemExit('Maven is required at build time to prepare the HYDRA offline runtime vendor')
                with tempfile.TemporaryDirectory(prefix='hydra-maven-vendor-') as tmp:
                    tmp=Path(tmp); deps=tmp/'deps'; deps.mkdir()
                    pom=tmp/'pom.xml'; entries=''.join('<dependency><groupId>'+c.split(':')[0]+'</groupId><artifactId>'+c.split(':')[1]+'</artifactId><version>'+c.split(':')[2]+'</version></dependency>' for c in coordinates)
                    pom.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion><groupId>hydra.vendor</groupId><artifactId>runtime</artifactId><version>1</version><dependencies>'+entries+'</dependencies></project>',encoding='utf-8')
                    subprocess.check_call([mvn,'-q','-f',str(pom),'dependency:copy-dependencies','-DincludeScope=runtime','-DoutputDirectory='+str(deps)])
                    for jar in deps.glob('*.jar'): shutil.copy2(jar,lib/jar.name)
                jars=sorted(lib.glob('*.jar')); manifest={'schemaVersion':'1.0','coordinates':coordinates,'jars':[{'name':p.name,'size':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in jars]}
                root.joinpath('maven-runtime-manifest.json').write_text(json.dumps(manifest,indent=2)+'\\n',encoding='utf-8')
                if coordinates and not jars: raise SystemExit('HYDRA Maven runtime vendor is empty')
                print(f'HYDRA_MAVEN_RUNTIME_VENDOR_READY jars={len(jars)}')
                """.formatted(array);
    }

    /** Descripción: Ejecuta la operación «mavenVerify» correspondiente a la responsabilidad de TechnologyAdapterSelfContainedDependencySupport. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private static String mavenVerify() {
        return """
                from pathlib import Path
                import hashlib,json
                root=Path(__file__).resolve().parent; manifest_path=root/'maven-runtime-manifest.json'; lib=root/'lib'
                if not manifest_path.is_file(): raise SystemExit('HYDRA Maven runtime manifest is missing')
                manifest=json.loads(manifest_path.read_text(encoding='utf-8'))
                for entry in manifest.get('jars',[]):
                    path=lib/entry['name']
                    if not path.is_file(): raise SystemExit('Missing HYDRA Maven runtime jar: '+entry['name'])
                    if path.stat().st_size != entry['size'] or hashlib.sha256(path.read_bytes()).hexdigest()!=entry['sha256']:
                        raise SystemExit('Corrupt HYDRA Maven runtime jar: '+entry['name'])
                if manifest.get('coordinates') and not manifest.get('jars'): raise SystemExit('HYDRA Maven runtime vendor is empty')
                print('HYDRA_MAVEN_RUNTIME_VERIFY=PASS')
                """;
    }

    /** Descripción: Valida las condiciones necesarias para «requireSafeP2Unit» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void requireSafeP2Unit(String value) {
        if(!value.matches("[A-Za-z0-9_.-]+")) throw new IllegalArgumentException("Unsafe p2 installable unit: " + value);
    }

    /** Descripción: Ejecuta la operación «packageName» correspondiente a la responsabilidad de TechnologyAdapterSelfContainedDependencySupport. Entradas: spec: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String packageName(String spec) {
        String value = spec.trim();
        if(value.startsWith("@")) {
            int second = value.indexOf('@', 1);
            return second > 0 ? value.substring(0, second) : value;
        }
        int at = value.indexOf('@');
        return at > 0 ? value.substring(0, at) : value;
    }
    /** Descripción: Ejecuta la operación «xml» correspondiente a la responsabilidad de TechnologyAdapterSelfContainedDependencySupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String xml(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    /** Descripción: Ejecuta la operación «json» correspondiente a la responsabilidad de TechnologyAdapterSelfContainedDependencySupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String json(String value){return value.replace("\\","\\\\").replace("\"","\\\"");}
    /** Descripción: Ejecuta la operación «js» correspondiente a la responsabilidad de TechnologyAdapterSelfContainedDependencySupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String js(String value){return value.replace("\\","\\\\").replace("'","\\'");}
    /** Descripción: Ejecuta la operación «py» correspondiente a la responsabilidad de TechnologyAdapterSelfContainedDependencySupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String py(String value){return value.replace("\\","\\\\").replace("'","\\'");}
}
