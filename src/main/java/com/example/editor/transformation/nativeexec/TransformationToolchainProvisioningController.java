package com.example.editor.transformation.nativeexec;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.editor.runtime.job.HydraJobExecutionService;
import com.example.editor.runtime.job.HydraJobSnapshot;
import com.example.editor.transformation.export.TransformationExportLanguage;

/** Administrative API for installing only trusted server-side transformation toolchain packages. */
@RestController
@RequestMapping("/api/transformations/parity/native")
public class TransformationToolchainProvisioningController {
    private final TransformationToolchainProvisioningService service;
    private final HydraJobExecutionService jobs;

    /** Descripción: Inicializa el controlador con el servicio de provisionado y el executor transversal acotado. Entradas: service: servicio de provisionado; jobs: gestor global de trabajos. Salida: deja el controlador preparado. */
    public TransformationToolchainProvisioningController(TransformationToolchainProvisioningService service, HydraJobExecutionService jobs) {
        this.service = service;
        this.jobs = jobs;
    }

    @GetMapping("/toolchains")
    /** Descripción: Devuelve catálogo confiable e instalaciones administradas. Entradas: no requiere parámetros. Salida: catálogo actual. */
    public TransformationToolchainCatalog catalog() {
        return service.catalog();
    }

    @PostMapping("/toolchains/{language}/install")
    /** Descripción: Instala o sustituye de forma síncrona un paquete exacto del catálogo confiable. Entradas: language: tecnología; request: paquete solicitado. Salida: informe de provisionado. */
    public TransformationToolchainProvisioningReport install(@PathVariable TransformationExportLanguage language,
            @RequestBody TransformationToolchainProvisioningRequest request) {
        return service.install(language, request);
    }

    @PostMapping("/toolchains/{language}/install/jobs")
    /** Descripción: Encola la instalación o sustitución de un toolchain confiable en el executor global. Entradas: language: tecnología; request: paquete; correlationId: correlación opcional. Salida: trabajo registrado; el catálogo refleja el resultado al completar. */
    public HydraJobSnapshot installAsync(@PathVariable TransformationExportLanguage language,
            @RequestBody TransformationToolchainProvisioningRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return jobs.submit("TOOLCHAIN_INSTALL", "Install transformation toolchain " + language.name(), correlationId, context -> {
            context.progress(5, "VALIDATING", "Validating trusted toolchain package");
            context.throwIfCancellationRequested();
            TransformationToolchainProvisioningReport report = service.install(language, request);
            context.progress(95, "PUBLISHED", "Toolchain provisioning finished with status " + report.status());
        });
    }

    @PostMapping("/toolchains/{language}/uninstall")
    /** Descripción: Retira de forma síncrona un bundle instalado previamente por HYDRA. Entradas: language: tecnología; request: opciones. Salida: informe de retirada. */
    public TransformationToolchainProvisioningReport uninstall(@PathVariable TransformationExportLanguage language,
            @RequestBody(required = false) TransformationToolchainRemovalRequest request) {
        return service.uninstall(language, request);
    }

    @PostMapping("/toolchains/{language}/uninstall/jobs")
    /** Descripción: Encola la retirada de un toolchain administrado en el executor global. Entradas: language: tecnología; request: opciones de retirada; correlationId: correlación opcional. Salida: trabajo registrado. */
    public HydraJobSnapshot uninstallAsync(@PathVariable TransformationExportLanguage language,
            @RequestBody(required = false) TransformationToolchainRemovalRequest request,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        return jobs.submit("TOOLCHAIN_UNINSTALL", "Uninstall transformation toolchain " + language.name(), correlationId, context -> {
            context.progress(5, "VALIDATING", "Validating managed toolchain removal");
            context.throwIfCancellationRequested();
            TransformationToolchainProvisioningReport report = service.uninstall(language, request);
            context.progress(95, "PUBLISHED", "Toolchain removal finished with status " + report.status());
        });
    }
}
