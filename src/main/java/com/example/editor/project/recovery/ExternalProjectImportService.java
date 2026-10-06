package com.example.editor.project.recovery;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.example.editor.project.CreateLocalProjectRequest;
import com.example.editor.project.ProjectApplicationService;
import com.example.editor.project.OpenLocalProjectRequest;
import com.example.editor.project.ProjectException;
import com.example.editor.project.ProjectSession;
import com.example.editor.project.WorkspaceTransferMode;
import com.example.editor.project.storage.ProjectDeletionService;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Materializa un proyecto externo como proyecto HYDRA revisable y no destructivo. */
@Service
public class ExternalProjectImportService {
    private final ExternalProjectRecoveryService recovery;
    private final ExternalProjectArchiveWalker walker;
    private final ProjectApplicationService projects;
    private final ProjectDeletionService deletion;
    private final ExternalProjectSemanticMaterializationService materialization;
    private final ObjectMapper mapper;

    /** Inicializa la importación externa y su materializador semántico multi-tecnología. */
    /** Descripción: Inicializa una instancia de ExternalProjectImportService con sus dependencias y datos de entrada. Entradas: recovery: dato de entrada necesario para la operación; walker: dato de entrada necesario para la operación; projects: dato de entrada necesario para la operación; deletion: dato de entrada necesario para la operación; materialization: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public ExternalProjectImportService(ExternalProjectRecoveryService recovery, ExternalProjectArchiveWalker walker,
            ProjectApplicationService projects, ProjectDeletionService deletion,
            ExternalProjectSemanticMaterializationService materialization, ObjectMapper mapper) {
        this.recovery = recovery;
        this.walker = walker;
        this.projects = projects;
        this.deletion = deletion;
        this.materialization = materialization;
        this.mapper = mapper;
    }

    /** Analiza y copia el proyecto externo preservando el original bajo external-source/. */
    /** Descripción: Ejecuta la operación «importProject» correspondiente a la responsabilidad de ExternalProjectImportService. Entradas: sourceName: dato de entrada necesario para la operación; archive: archivo o contenido que debe procesarse; compressedBytes: dato de entrada necesario para la operación; parentDirectory: dato de entrada necesario para la operación; projectName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public ExternalProjectImportResult importProject(String sourceName, InputStream archive, long compressedBytes,
            String parentDirectory, String projectName) {
        if(archive == null || compressedBytes <= 0) throw new ProjectException(ProjectException.Reason.INVALID_REQUEST,
                "Select an external project archive before importing it");
        var entries = walker.walk(sourceName, archive, compressedBytes);
        ExternalProjectRecoveryReport report = recovery.analyzeEntries(entries);
        String name = required(projectName, "Project name");
        String projectId = slug(name) + '-' + report.fingerprint().substring(0, 8);
        ProjectSession previous = projects.session();
        boolean created = false;
        try {
            projects.create(new CreateLocalProjectRequest(required(parentDirectory, "Destination directory"), projectId,
                    name, "Imported external modeling project", false));
            created = true;
            for(ExternalProjectArchiveEntry entry : entries) {
                String portable = externalPath(entry.path());
                int slash = portable.lastIndexOf('/');
                String directory = slash < 0 ? "external-source" : "external-source/" + portable.substring(0, slash);
                String fileName = slash < 0 ? portable : portable.substring(slash + 1);
                projects.upload(directory, fileName, entry.bytes());
            }
            projects.upload("recovery", "external-project-analysis.json", serialize(report));
            ExternalProjectMaterializationResult materialized = materialization.materialize(entries, report);
            projects.upload("recovery", "external-project-materialization.json", serialize(materialized));
            projects.upload("recovery", "README.md", recoveryReadme(report, materialized).getBytes(StandardCharsets.UTF_8));
            return new ExternalProjectImportResult(projects.session(), report, materialized);
        } catch(RuntimeException failure) {
            if(created) {
                try { deletion.deleteActiveProject(true); } catch(RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            }
            restore(previous, failure);
            throw failure;
        }
    }

    /** Descripción: Ejecuta la operación «restore» correspondiente a la responsabilidad de ExternalProjectImportService. Entradas: previous: dato de entrada necesario para la operación; failure: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void restore(ProjectSession previous, RuntimeException failure) {
        if(previous == null || !previous.active() || previous.directory() == null || previous.directory().isBlank()) return;
        try { projects.open(new OpenLocalProjectRequest(previous.directory(), false, WorkspaceTransferMode.MERGE)); }
        catch(RuntimeException restoreFailure) { failure.addSuppressed(restoreFailure); }
    }

    /** Persiste un informe de recuperación o materialización con el mismo serializador canónico. */
    /** Descripción: Ejecuta la operación «serialize» correspondiente a la responsabilidad de ExternalProjectImportService. Entradas: report: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] serialize(Object report) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
        } catch(JacksonException exception) {
            throw new ProjectException(ProjectException.Reason.STORAGE_ERROR,
                    "Could not persist the external-project recovery report", exception);
        }
    }

    /** Resume el resultado sin ocultar qué capas quedaron solo preservadas para revisión. */
    /** Descripción: Ejecuta la operación «recoveryReadme» correspondiente a la responsabilidad de ExternalProjectImportService. Entradas: report: dato de entrada necesario para la operación; materialized: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String recoveryReadme(ExternalProjectRecoveryReport report, ExternalProjectMaterializationResult materialized) {
        return "# External project recovery\n\n"
                + "HYDRA preserved the complete imported project under `external-source/`.\n\n"
                + "Detected technologies: " + String.join(", ", report.technologies()) + "\n\n"
                + "Canonical metamodel: " + (materialized.metamodelId().isBlank() ? "not materialized automatically" : materialized.metamodelId()) + "\n\n"
                + "Editors materialized: " + materialized.editorIds().size() + "\n\n"
                + "Validation rules materialized: " + (materialized.visualValidationsImported() + materialized.validationRulesImported()) + "\n\n"
                + "Quick fixes materialized: " + materialized.quickFixesImported() + " (review-only: " + materialized.reviewOnlyQuickFixes() + ")\n\n"
                + "Review `external-project-analysis.json` and `external-project-materialization.json` before accepting partial or ambiguous mappings. "
                + "HYDRA never executes imported plug-ins, validators or quick-fix source while recovering the project.\n";
    }

    /** Descripción: Ejecuta la operación «externalPath» correspondiente a la responsabilidad de ExternalProjectImportService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String externalPath(String path) {
        String normalized = path.replace("!/", ".__archive_contents__/");
        return java.util.Arrays.stream(normalized.split("/", -1))
                .map(this::encodePathSegment)
                .collect(java.util.stream.Collectors.joining("/"));
    }

    /** Descripción: Ejecuta la operación «encodePathSegment» correspondiente a la responsabilidad de ExternalProjectImportService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String encodePathSegment(String value) {
        if(value == null || value.isEmpty()) return "_empty_";
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(utf8.length);
        for(byte item : utf8) {
            int current = item & 0xff;
            char character = (char) current;
            boolean safe = current < 128 && (Character.isLetterOrDigit(character)
                    || character == '.' || character == '_' || character == '-'
                    || character == '(' || character == ')' || character == '@' || character == '+');
            if(safe) encoded.append(character);
            else encoded.append('~').append(String.format(Locale.ROOT, "%02X", current));
        }
        String result = encoded.toString();
        if(result.equals(".") || result.equals("..")) return result.replace(".", "~2E");
        return result;
    }

    /** Descripción: Ejecuta la operación «slug» correspondiente a la responsabilidad de ExternalProjectImportService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String slug(String value) {
        String slug = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.isBlank() ? "external-project" : slug;
    }

    /** Descripción: Valida las condiciones necesarias para «required» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; label: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String required(String value, String label) {
        if(value == null || value.isBlank()) throw new ProjectException(ProjectException.Reason.INVALID_REQUEST, label + " is required");
        return value.trim();
    }
}
