package com.example.editor.config.governance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import com.example.editor.metamodel.importing.MetamodelImportProperties;
import com.example.editor.metamodel.importing.python.PythonAstImportProperties;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteImportProperties;
import com.example.editor.portability.archive.CapabilityPackageArchiveProperties;
import com.example.editor.project.LocalProjectProperties;
import com.example.editor.remote.auth.RemoteAuthProperties;
import com.example.editor.remote.git.NativeGitProperties;
import com.example.editor.security.input.HydraInputSecurityProperties;

/** Exposes an explicit whitelist of non-secret technical settings and applies only safe runtime mutations. */
@Service
public class HydraTechnicalSettingsService {
    private final Environment environment;
    private final LocalProjectProperties projects;
    private final MetamodelImportProperties metamodel;
    private final PythonAstImportProperties python;
    private final UpdateSiteImportProperties updateSite;
    private final CapabilityPackageArchiveProperties packages;
    private final RemoteAuthProperties remoteAuth;
    private final NativeGitProperties git;
    private final HydraInputSecurityProperties input;

    /** Descripción: Inicializa una instancia de HydraTechnicalSettingsService con sus dependencias y datos de entrada. Entradas: environment: dato de entrada necesario para la operación; projects: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; python: dato de entrada necesario para la operación; updateSite: dato de entrada necesario para la operación; packages: dato de entrada necesario para la operación; remoteAuth: dato de entrada necesario para la operación; git: dato de entrada necesario para la operación; input: valor de entrada que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ public HydraTechnicalSettingsService(Environment environment, LocalProjectProperties projects,
            MetamodelImportProperties metamodel, PythonAstImportProperties python, UpdateSiteImportProperties updateSite,
            CapabilityPackageArchiveProperties packages, RemoteAuthProperties remoteAuth, NativeGitProperties git,
            HydraInputSecurityProperties input) {
        this.environment=environment; this.projects=projects; this.metamodel=metamodel; this.python=python;
        this.updateSite=updateSite; this.packages=packages; this.remoteAuth=remoteAuth; this.git=git; this.input=input;
    }

    /** Descripción: Ejecuta la operación «snapshot» correspondiente a la responsabilidad de HydraTechnicalSettingsService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public HydraTechnicalSettingsSnapshot snapshot() {
        List<HydraTechnicalSetting> values = new ArrayList<>();
        add(values,"runtime.headless","Runtime",Boolean.toString(Boolean.parseBoolean(environment.getProperty("spring.main.headless","false"))),false,"Whether the backend runs without desktop UI facilities.");
        add(values,"dsl.projects.local.native-path-picker-enabled","Projects",projects.isNativePathPickerEnabled(),false,"Desktop native file/directory picker availability; profile-bound at startup.");
        add(values,"dsl.projects.local.native-path-picker-local-only","Projects",projects.isNativePathPickerLocalOnly(),false,"Restricts native path selection to local clients.");
        add(values,"dsl.projects.local.max-upload-file-size-bytes","Projects",projects.getMaxUploadFileSizeBytes(),false,"Maximum project upload size.");
        add(values,"dsl.imports.metamodel.max-file-size-bytes","Imports",metamodel.getMaxFileSizeBytes(),false,"Maximum standalone metamodel import size.");
        add(values,"dsl.imports.metamodel.python.fallback-enabled","Imports",python.isFallbackEnabled(),true,"Allows the bounded fallback parser when the Python AST process is unavailable.");
        add(values,"dsl.imports.metamodel.python.timeout-seconds","Imports",python.getTimeoutSeconds(),false,"Python AST process timeout.");
        add(values,"dsl.imports.update-site.max-upload-bytes","Update Site",updateSite.getMaxUploadBytes(),false,"Maximum compressed update-site upload.");
        add(values,"dsl.imports.update-site.max-entry-bytes","Update Site",updateSite.getMaxEntryBytes(),false,"Maximum individual extracted entry.");
        add(values,"dsl.imports.update-site.max-uncompressed-bytes","Update Site",updateSite.getMaxUncompressedBytes(),false,"Maximum aggregate uncompressed bytes.");
        add(values,"dsl.imports.update-site.max-compression-ratio","Update Site",updateSite.getMaxCompressionRatio(),false,"Maximum accepted archive compression ratio.");
        add(values,"dsl.imports.update-site.max-archive-read-millis","Update Site",updateSite.getMaxArchiveReadMillis(),false,"Wall-clock analysis budget.");
        add(values,"dsl.imports.update-site.max-archive-cpu-millis","Update Site",updateSite.getMaxArchiveCpuMillis(),false,"CPU analysis budget.");
        add(values,"dsl.imports.update-site.repository-snapshot-max-count","Update Site",updateSite.getRepositorySnapshotMaxCount(),false,"Maximum retained repository snapshots.");
        add(values,"dsl.imports.update-site.repository-snapshot-retention-hours","Update Site",updateSite.getRepositorySnapshotRetentionHours(),false,"Repository snapshot retention.");
        add(values,"dsl.imports.update-site.remote-cache-enabled","Update Site",updateSite.isRemoteCacheEnabled(),true,"Enables bounded remote update-site cache reuse.");
        add(values,"dsl.imports.update-site.analysis-cache-entries","Update Site",updateSite.getAnalysisCacheEntries(),true,"Maximum in-memory update-site analysis cache entries.");
        add(values,"dsl.imports.update-site.analysis-cache-ttl-seconds","Update Site",updateSite.getAnalysisCacheTtlSeconds(),true,"Update-site analysis cache TTL.");
        add(values,"dsl.portability.packages.cache-enabled","Packages",packages.isCacheEnabled(),true,"Enables local capability-package cache reuse.");
        add(values,"dsl.portability.packages.require-signature","Packages",packages.isRequireSignature(),false,"Requires authenticity signatures on imported capability packages.");
        add(values,"dsl.portability.packages.maximum-archive-bytes","Packages",packages.getMaximumArchiveBytes(),false,"Maximum capability-package archive size.");
        add(values,"hydra.remote-auth.allow-loopback-http","Remote access",remoteAuth.isAllowLoopbackHttp(),false,"Allows HTTP only for loopback OAuth callbacks.");
        add(values,"hydra.native-git.enabled","External tools",git.isEnabled(),false,"Enables the allowlisted native Git adapter.");
        add(values,"hydra.native-git.maximum-output-bytes","External tools",git.getMaximumOutputBytes(),false,"Maximum retained Git process output.");
        add(values,"hydra.security.input.max-structured-request-bytes","Security",input.getMaxStructuredRequestBytes(),false,"Maximum ordinary structured HTTP request size.");
        add(values,"hydra.security.input.max-multipart-request-bytes","Security",input.getMaxMultipartRequestBytes(),false,"Maximum multipart HTTP request size.");
        return new HydraTechnicalSettingsSnapshot(List.copyOf(Arrays.asList(environment.getActiveProfiles())), List.copyOf(values));
    }

    /** Descripción: Actualiza el estado gestionado por HydraTechnicalSettingsService mediante la operación «update». Entradas: requested: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public HydraTechnicalSettingsSnapshot update(Map<String,String> requested) {
        for(Map.Entry<String,String> entry : requested.entrySet()) {
            String key=entry.getKey(); String value=entry.getValue();
            switch(key) {
                case "dsl.imports.metamodel.python.fallback-enabled" -> python.setFallbackEnabled(bool(key,value));
                case "dsl.imports.update-site.remote-cache-enabled" -> updateSite.setRemoteCacheEnabled(bool(key,value));
                case "dsl.imports.update-site.analysis-cache-entries" -> updateSite.setAnalysisCacheEntries(integer(key,value,0,128));
                case "dsl.imports.update-site.analysis-cache-ttl-seconds" -> updateSite.setAnalysisCacheTtlSeconds(integer(key,value,1,86_400));
                case "dsl.portability.packages.cache-enabled" -> packages.setCacheEnabled(bool(key,value));
                default -> throw new IllegalArgumentException("Technical setting is read-only or not exposed: " + key);
            }
        }
        return snapshot();
    }

    /** Descripción: Ejecuta la operación «add» correspondiente a la responsabilidad de HydraTechnicalSettingsService. Entradas: out: dato de entrada necesario para la operación; key: identificador que delimita el recurso objetivo; category: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse; mutable: dato de entrada necesario para la operación; description: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void add(List<HydraTechnicalSetting> out,String key,String category,Object value,boolean mutable,String description){out.add(new HydraTechnicalSetting(key,category,String.valueOf(value),mutable,description));}
    /** Descripción: Ejecuta la operación «bool» correspondiente a la responsabilidad de HydraTechnicalSettingsService. Entradas: key: identificador que delimita el recurso objetivo; value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean bool(String key,String value){if("true".equalsIgnoreCase(value))return true;if("false".equalsIgnoreCase(value))return false;throw new IllegalArgumentException(key+" must be true or false");}
    /** Descripción: Ejecuta la operación «integer» correspondiente a la responsabilidad de HydraTechnicalSettingsService. Entradas: key: identificador que delimita el recurso objetivo; value: valor de entrada que debe procesarse; min: dato de entrada necesario para la operación; max: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static int integer(String key,String value,int min,int max){try{int parsed=Integer.parseInt(value);if(parsed<min||parsed>max)throw new IllegalArgumentException(key+" must be between "+min+" and "+max);return parsed;}catch(NumberFormatException ex){throw new IllegalArgumentException(key+" must be an integer",ex);}}
}
