package com.example.editor.definition.importing;

import java.util.ArrayList;
import java.util.List;

import com.example.editor.definition.EdgeMapping;
import com.example.editor.definition.EditorTool;
import com.example.editor.definition.NodeMapping;
import com.example.editor.definition.PaletteGroup;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.metamodel.core.MetaModel;

/** Mutable import-only draft; never persisted directly. */
final class VisualDefinitionImportDraft {
    final String technology;
    final MetaModel metamodel;
    final List<RepresentationDefinition> representations = new ArrayList<>();
    final List<NodeMapping> nodeMappings = new ArrayList<>();
    final List<EdgeMapping> edgeMappings = new ArrayList<>();
    final List<PaletteGroup> paletteGroups = new ArrayList<>();
    final List<EditorTool> tools = new ArrayList<>();
    final List<VisualDefinitionImportDiagnostic> diagnostics = new ArrayList<>();
    final List<VisualDefinitionImportFix> fixes = new ArrayList<>();
    final List<String> javaServices = new ArrayList<>();
    final List<String> validations = new ArrayList<>();
    final List<VisualImportedValidation> validationDetails = new ArrayList<>();
    VisualMetamodelAssociation association;
    final List<String> expressions = new ArrayList<>();
    final List<String> preserved = new ArrayList<>();
    final List<String> artifacts = new ArrayList<>();
    int elementsInspected;
    int referencesChecked;
    int unresolvedReferences;
    int ambiguousReferences;
    int invalidParameters;
    int structuralProblems;

    /** Descripción: Inicializa una instancia de VisualDefinitionImportDraft con sus dependencias y datos de entrada. Entradas: technology: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ VisualDefinitionImportDraft(String technology, MetaModel metamodel) {
        this.technology = technology;
        this.metamodel = metamodel;
    }

    /** Descripción: Ejecuta la operación «elementInspected» correspondiente a la responsabilidad de VisualDefinitionImportDraft. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void elementInspected() { elementsInspected++; }
    /** Descripción: Ejecuta la operación «referenceChecked» correspondiente a la responsabilidad de VisualDefinitionImportDraft. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void referenceChecked() { referencesChecked++; }
    /** Descripción: Ejecuta la operación «unresolved» correspondiente a la responsabilidad de VisualDefinitionImportDraft. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void unresolved() { unresolvedReferences++; }
    /** Descripción: Ejecuta la operación «ambiguous» correspondiente a la responsabilidad de VisualDefinitionImportDraft. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void ambiguous() { ambiguousReferences++; }
    /** Descripción: Ejecuta la operación «invalidParameter» correspondiente a la responsabilidad de VisualDefinitionImportDraft. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void invalidParameter() { invalidParameters++; }
    /** Descripción: Ejecuta la operación «structuralProblem» correspondiente a la responsabilidad de VisualDefinitionImportDraft. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void structuralProblem() { structuralProblems++; }

    /** Descripción: Ejecuta la operación «diag» correspondiente a la responsabilidad de VisualDefinitionImportDraft. Entradas: severity: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void diag(String severity, String code, String source, String message) {
        diagnostics.add(new VisualDefinitionImportDiagnostic(severity, code, source, "", "", message, "", "", List.of(), "UNKNOWN"));
    }

    /** Descripción: Ejecuta la operación «diag» correspondiente a la responsabilidad de VisualDefinitionImportDraft. Entradas: severity: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; location: dato de entrada necesario para la operación; element: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación; explanation: dato de entrada necesario para la operación; suggestedAction: dato de entrada necesario para la operación; fixIds: dato de entrada necesario para la operación; confidence: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void diag(String severity, String code, String source, String location, String element, String message,
            String explanation, String suggestedAction, List<String> fixIds, String confidence) {
        diagnostics.add(new VisualDefinitionImportDiagnostic(severity, code, source, location, element, message,
                explanation, suggestedAction, fixIds, confidence));
    }

    /** Descripción: Ejecuta la operación «fix» correspondiente a la responsabilidad de VisualDefinitionImportDraft. Entradas: id: identificador que delimita el recurso objetivo; code: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; title: dato de entrada necesario para la operación; description: dato de entrada necesario para la operación; safety: dato de entrada necesario para la operación; confidence: dato de entrada necesario para la operación; automatic: dato de entrada necesario para la operación; applied: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ VisualDefinitionImportFix fix(String id, String code, String source, String target, String title,
            String description, String safety, String confidence, boolean automatic, boolean applied) {
        VisualDefinitionImportFix fix = new VisualDefinitionImportFix(id, code, source, target, title, description,
                safety, confidence, automatic, applied);
        fixes.add(fix);
        return fix;
    }

    /** Descripción: Ejecuta la operación «analysis» correspondiente a la responsabilidad de VisualDefinitionImportDraft. Entradas: importReady: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ VisualDefinitionImportAnalysis analysis(boolean importReady) {
        int warnings = (int) diagnostics.stream().filter(value -> "WARNING".equals(value.severity())).count();
        int safe = (int) fixes.stream().filter(value -> "SAFE".equals(value.safety())).count();
        int review = (int) fixes.stream().filter(value -> "REVIEW".equals(value.safety())).count();
        int manual = (int) fixes.stream().filter(value -> "MANUAL".equals(value.safety())).count();
        return new VisualDefinitionImportAnalysis(artifacts.size(), elementsInspected, referencesChecked,
                unresolvedReferences, ambiguousReferences, invalidParameters, structuralProblems, warnings,
                safe, review, manual, importReady);
    }
}
