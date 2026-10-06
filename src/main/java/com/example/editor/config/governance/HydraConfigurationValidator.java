package com.example.editor.config.governance;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import com.example.editor.metamodel.importing.MetamodelImportProperties;
import com.example.editor.metamodel.importing.updatesite.UpdateSiteImportProperties;
import com.example.editor.portability.archive.CapabilityPackageArchiveProperties;
import com.example.editor.project.LocalProjectProperties;
import com.example.editor.remote.auth.RemoteAuthProperties;
import com.example.editor.security.input.HydraInputSecurityProperties;

/** Fails fast when independently valid limits form an unsafe or contradictory profile. */
@Component
public class HydraConfigurationValidator implements SmartInitializingSingleton {
    private final Environment environment;
    private final MetamodelImportProperties metamodel;
    private final UpdateSiteImportProperties updateSite;
    private final CapabilityPackageArchiveProperties packages;
    private final LocalProjectProperties projects;
    private final RemoteAuthProperties remoteAuth;
    private final HydraInputSecurityProperties input;

    /** Descripción: Inicializa una instancia de HydraConfigurationValidator con sus dependencias y datos de entrada. Entradas: environment: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; updateSite: dato de entrada necesario para la operación; packages: dato de entrada necesario para la operación; projects: dato de entrada necesario para la operación; remoteAuth: dato de entrada necesario para la operación; input: valor de entrada que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ public HydraConfigurationValidator(Environment environment, MetamodelImportProperties metamodel,
            UpdateSiteImportProperties updateSite, CapabilityPackageArchiveProperties packages,
            LocalProjectProperties projects, RemoteAuthProperties remoteAuth, HydraInputSecurityProperties input) {
        this.environment = environment;
        this.metamodel = metamodel;
        this.updateSite = updateSite;
        this.packages = packages;
        this.projects = projects;
        this.remoteAuth = remoteAuth;
        this.input = input;
    }

    @Override
    /** Descripción: Ejecuta la operación «afterSingletonsInstantiated» correspondiente a la responsabilidad de HydraConfigurationValidator. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void afterSingletonsInstantiated() {
        require(metamodel.getMaxFileSizeBytes() <= input.getMaxMultipartRequestBytes(),
                "dsl.imports.metamodel.max-file-size-bytes must not exceed hydra.security.input.max-multipart-request-bytes");
        require(updateSite.getMaxEntryBytes() <= updateSite.getMaxUncompressedBytes(),
                "dsl.imports.update-site.max-entry-bytes must not exceed max-uncompressed-bytes");
        require(updateSite.getRemoteMaxBytes() <= updateSite.getRemoteMaxTotalBytes(),
                "dsl.imports.update-site.remote-max-bytes must not exceed remote-max-total-bytes");
        require(packages.getMaximumEntryBytes() <= packages.getMaximumArchiveBytes(),
                "dsl.portability.packages.maximum-entry-bytes must not exceed maximum-archive-bytes");
        require(projects.getMaxEditableFileSizeBytes() <= projects.getMaxUploadFileSizeBytes(),
                "dsl.projects.local.max-editable-file-size-bytes must not exceed max-upload-file-size-bytes");
        require(projects.getMaxUploadFileSizeBytes() <= projects.getMaxArchiveSizeBytes(),
                "dsl.projects.local.max-upload-file-size-bytes must not exceed max-archive-size-bytes");
        require(input.getMaxStructuredRequestBytes() <= input.getMaxMultipartRequestBytes(),
                "hydra.security.input.max-structured-request-bytes must not exceed max-multipart-request-bytes");

        if(environment.acceptsProfiles(Profiles.of("production"))) {
            require(!remoteAuth.isAllowLoopbackHttp(), "production requires hydra.remote-auth.allow-loopback-http=false");
            require(packages.isRequireSignature(), "production requires dsl.portability.packages.require-signature=true");
            require(!packages.getSignatureSecret().isBlank(),
                    "production package signing requires dsl.portability.packages.signature-secret from an external secret source");
            require(!projects.isNativePathPickerEnabled(), "production must disable the native desktop path picker");
        }
    }

    /** Descripción: Valida las condiciones necesarias para «require» y rechaza entradas no válidas. Entradas: condition: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void require(boolean condition, String message) {
        if(!condition) throw new IllegalStateException("Invalid HYDRA configuration: " + message);
    }
}
