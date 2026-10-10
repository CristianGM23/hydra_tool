package com.example.editor.standalone.distribution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** UI-facing orchestration for planning, materializing, validating and packaging standalone editors. */
@Service
public class StandaloneDistributionApplicationService {
    private final StandaloneDistributionPlanner planner = new StandaloneDistributionPlanner();
    private final StandaloneDistributionMaterializer materializer;
    private final StandaloneDistributionValidationService validator;
    private final StandaloneDistributionPackager packager;
    private final Path allowedRoot;

    @Autowired
    /** Descripción: Inicializa una instancia de StandaloneDistributionApplicationService con sus dependencias y datos de entrada. Entradas: materializer: dato de entrada necesario para la operación; validator: dato de entrada necesario para la operación; packager: dato de entrada necesario para la operación; allowedRoot: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public StandaloneDistributionApplicationService(StandaloneDistributionMaterializer materializer,
            StandaloneDistributionValidationService validator, StandaloneDistributionPackager packager,
            @Value("${hydra.standalone-distribution.allowed-root:data/standalone-editors}") String allowedRoot){
        this(materializer, validator, packager, Path.of(allowedRoot));
    }

    /** Descripción: Inicializa una instancia de StandaloneDistributionApplicationService con sus dependencias y datos de entrada. Entradas: materializer: dato de entrada necesario para la operación; validator: dato de entrada necesario para la operación; packager: dato de entrada necesario para la operación; allowedRoot: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ StandaloneDistributionApplicationService(StandaloneDistributionMaterializer materializer,
            StandaloneDistributionValidationService validator, StandaloneDistributionPackager packager,
            Path allowedRoot){
        this.materializer = materializer; this.validator = validator; this.packager = packager;
        this.allowedRoot = allowedRoot.toAbsolutePath().normalize();
    }

    /** Descripción: Ejecuta la operación «plan» correspondiente a la responsabilidad de StandaloneDistributionApplicationService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public List<StandaloneDistributionArtifact> plan(StandaloneDistributionRequest request){return planner.plan(request.profile());}

    /** Descripción: Ejecuta la operación «materialize» correspondiente a la responsabilidad de StandaloneDistributionApplicationService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StandaloneDistributionPreparationResult materialize(StandaloneDistributionRequest request){
        Path editorRoot = resolveEditorRoot(request.editorDirectory());
        StandaloneDistributionMaterializationResult materialization = materializer.materialize(editorRoot, planner.plan(request.profile()));
        StandaloneDistributionValidationReport validation = validator.validate(editorRoot, request.profile());
        return new StandaloneDistributionPreparationResult(materialization, validation);
    }

    /** Descripción: Valida las condiciones necesarias para «validate» y rechaza entradas no válidas. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StandaloneDistributionValidationReport validate(StandaloneDistributionRequest request){
        return validator.validate(resolveEditorRoot(request.editorDirectory()), request.profile());
    }

    /**Descripción: Calcula la preparación estática para el despliegue sin afirmar que el instalador está compilado. Entradas: datos validados por el contrato. Salida: resultado verificable de la operación. */
    public StandaloneDistributionValidationReport releaseReadiness(StandaloneDistributionRequest request) {
        Path editorRoot = resolveEditorRoot(request.editorDirectory());
        return new StandaloneProductionReadinessService().assess(editorRoot, request.profile(),
                validator.validate(editorRoot, request.profile()));
    }

    /**Descripción: Empaqueta el editor solo cuando los artefactos web y de distribución superan el gate de publicación. Entradas: datos validados por el contrato. Salida: resultado verificable de la operación. */
    public StandaloneDistributionDownload packageReleaseReady(StandaloneDistributionRequest request) {
        Path editorRoot = resolveEditorRoot(request.editorDirectory());
        StandaloneDistributionValidationReport readiness = releaseReadiness(request);
        if(!readiness.valid()) throw new IllegalStateException("Standalone distribution is not release-ready: "
                + readiness.diagnostics().stream().filter(d -> d.severity() == StandaloneDistributionDiagnosticSeverity.ERROR)
                        .map(StandaloneDistributionDiagnostic::code).distinct().toList());
        Path archive = allowedRoot.resolve(".hydra-packages")
                .resolve(token(request.profile().productName()) + "-" + token(request.profile().version()) + "-release-source.zip").normalize();
        if(archive.startsWith(editorRoot)) throw new IllegalArgumentException("Package directory must be outside the editor root");
        try {
            Files.createDirectories(archive.getParent());
            StandaloneDistributionPackageResult result = packager.packageProject(editorRoot, archive, readiness);
            return new StandaloneDistributionDownload(archive.getFileName().toString(), result.sha256(),
                    com.example.editor.io.BoundedIo.readInternal(result.archive()));
        } catch(IOException exception) { throw new IllegalStateException("Could not read standalone release-source archive", exception); }
    }

    /** Descripción: Empaqueta solo recursos web compilados con integridad verificada. Entradas: solicitud de distribución. Salida: ZIP web descargable sin afirmar que sea instalador nativo. */
    public StandaloneDistributionDownload packageDeployableWeb(StandaloneDistributionRequest request) {
        Path editorRoot = resolveEditorRoot(request.editorDirectory());
        StandaloneDistributionValidationReport readiness = releaseReadiness(request);
        if(!readiness.valid()) throw new IllegalStateException("Built web editor is not release-ready");
        Path archive = allowedRoot.resolve(".hydra-packages")
                .resolve(token(request.profile().productName()) + "-" + token(request.profile().version()) + "-web.zip");
        StandaloneDistributionPackageResult result = new StandaloneWebDeploymentPackager()
                .packageWeb(editorRoot, archive, request.profile(), readiness);
        try { return new StandaloneDistributionDownload(archive.getFileName().toString(), result.sha256(),
                    com.example.editor.io.BoundedIo.readInternal(result.archive())); }
        catch(IOException exception) { throw new IllegalStateException("Could not read deployable web package", exception); }
    }

    /** Descripción: Ejecuta la operación «packageEditor» correspondiente a la responsabilidad de StandaloneDistributionApplicationService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StandaloneDistributionDownload packageEditor(StandaloneDistributionRequest request){
        Path editorRoot = resolveEditorRoot(request.editorDirectory());
        StandaloneDistributionValidationReport validation = validator.validate(editorRoot, request.profile());
        Path packageRoot = allowedRoot.resolve(".hydra-packages").normalize();
        if(packageRoot.startsWith(editorRoot)){throw new IllegalArgumentException("Package directory must be outside the editor root");}
        String fileName = token(request.profile().productName()) + "-" + token(request.profile().version()) + ".zip";
        Path archive = packageRoot.resolve(fileName).normalize();
        try{
            Files.createDirectories(packageRoot);
            StandaloneDistributionPackageResult result = packager.packageProject(editorRoot, archive, validation);
            return new StandaloneDistributionDownload(fileName, result.sha256(), com.example.editor.io.BoundedIo.readInternal(result.archive()));
        }catch(IOException exception){throw new IllegalStateException("Could not read packaged standalone editor", exception);}
    }

    /** Descripción: Obtiene la información correspondiente a «resolveEditorRoot» dentro de StandaloneDistributionApplicationService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private Path resolveEditorRoot(String value){
        String normalized = value == null ? "" : value.replace('\\', '/').trim();
        if(normalized.isBlank() || normalized.startsWith("/") || normalized.equals(".") || normalized.equals("..") || normalized.contains("../")){
            throw new IllegalArgumentException("editorDirectory must be a safe relative directory below the configured distribution root");
        }
        Path root = allowedRoot.resolve(normalized).normalize();
        if(!root.startsWith(allowedRoot)){throw new IllegalArgumentException("editorDirectory escapes the configured distribution root");}
        rejectSymbolicSegments(root);
        return root;
    }

    /** Descripción: Ejecuta la operación «rejectSymbolicSegments» correspondiente a la responsabilidad de StandaloneDistributionApplicationService. Entradas: target: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void rejectSymbolicSegments(Path target){
        Path current = allowedRoot;
        for(Path segment : allowedRoot.relativize(target)){
            current = current.resolve(segment);
            if(Files.isSymbolicLink(current)){throw new IllegalArgumentException("editorDirectory crosses a symbolic link: " + segment);}
        }
    }

    /** Descripción: Ejecuta la operación «token» correspondiente a la responsabilidad de StandaloneDistributionApplicationService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String token(String value){
        String token = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        return token.isBlank() ? "hydra-editor" : token;
    }
}
