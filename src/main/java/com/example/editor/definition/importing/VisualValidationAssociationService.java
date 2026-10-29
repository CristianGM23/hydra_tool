package com.example.editor.definition.importing;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.example.editor.definition.EditorDefinitionModel;
import com.example.editor.definition.RepresentationDefinition;
import com.example.editor.metamodel.application.CanonicalMetamodelApplicationService;
import com.example.editor.metamodel.application.MetamodelApplicationException;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.semantic.expression.ExpressionValueKind;
import com.example.editor.semantic.expression.ExpressionValueType;
import com.example.editor.semantic.expression.LiteralExpressionNode;
import com.example.editor.validation.expression.ExpressionModelApplicationException;
import com.example.editor.validation.expression.ExpressionModelApplicationService;
import com.example.editor.validation.validation.AuthoritativeRepresentation;
import com.example.editor.validation.validation.ConversionStatus;
import com.example.editor.validation.validation.ImportedValidationSource;
import com.example.editor.validation.validation.ValidationContext;
import com.example.editor.validation.validation.ValidationContextKind;
import com.example.editor.validation.validation.ValidationExecutionMode;
import com.example.editor.validation.validation.ValidationModel;
import com.example.editor.validation.validation.ValidationModelApplicationException;
import com.example.editor.validation.validation.ValidationModelApplicationService;
import com.example.editor.validation.validation.ValidationProvenance;
import com.example.editor.validation.validation.ValidationRule;
import com.example.editor.validation.validation.ValidationRuleSet;
import com.example.editor.validation.validation.ValidationSeverity;
import com.example.editor.validation.validation.ValidationTrigger;
import com.example.editor.validation.importing.SafeExternalConstraintTranslator;

/** Persists the semantic association and imports ODesign validation semantics without pretending AQL is canonical executable logic. */
@Service
public class VisualValidationAssociationService {
    private final CanonicalMetamodelApplicationService metamodels;
    private final ExpressionModelApplicationService expressions;
    private final ValidationModelApplicationService validations;
    private final SafeExternalConstraintTranslator safeTranslator;

    /** Descripción: Inicializa una instancia de VisualValidationAssociationService con sus dependencias y datos de entrada. Entradas: metamodels: dato de entrada necesario para la operación; expressions: dato de entrada necesario para la operación; validations: dato de entrada necesario para la operación; safeTranslator: traductor declarativo que nunca ejecuta código importado. Salida: deja la instancia inicializada y lista para su uso. */ public VisualValidationAssociationService(CanonicalMetamodelApplicationService metamodels,
            ExpressionModelApplicationService expressions, ValidationModelApplicationService validations,
            SafeExternalConstraintTranslator safeTranslator) {
        this.metamodels = metamodels; this.expressions = expressions; this.validations = validations; this.safeTranslator = safeTranslator;
    }

    /** Descripción: Ejecuta la operación «commit» correspondiente a la responsabilidad de VisualValidationAssociationService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public VisualImportCommitResult commit(VisualImportCommitRequest request) {
        if(request == null || request.editorDefinition() == null || request.associatedMetamodel() == null)
            throw new IllegalArgumentException("The analysed visual editor and its associated metamodel are required before the import can be confirmed.");
        MetaModel associated = ensureMetamodel(request.associatedMetamodel());
        EditorDefinitionModel editor = request.editorDefinition();
        if(!associated.id().equals(editor.metamodelId()))
            throw new IllegalArgumentException("The visual editor is associated with metamodel '" + editor.metamodelId() + "' but the confirmed metamodel is '" + associated.id() + "'. Re-run the analysis with the correct association.");
        List<VisualImportedValidation> details = request.validationDetails();
        if(details.isEmpty()) return new VisualImportCommitResult(editor, associated.id(), 0, List.of());

        ExpressionState expressionState = loadExpressionState(associated.id());
        List<ExpressionDefinition> expressionDefinitions = new ArrayList<>(expressionState.model().expressions());
        Set<String> existingExpressionIds = new LinkedHashSet<>(); expressionDefinitions.forEach(value -> existingExpressionIds.add(value.id()));
        ValidationState state = loadValidationState(associated.id());
        List<ValidationRule> rules = new ArrayList<>(state.model().rules());
        Set<String> existingRuleIds = new LinkedHashSet<>(); for(ValidationRule rule: rules) existingRuleIds.add(rule.id());
        List<String> importedRuleIds = new ArrayList<>();
        Instant importedAt = Instant.now();
        for(VisualImportedValidation detail : details) {
            String fingerprint = editor.id()+"|"+detail.technology()+"|"+detail.source()+"|"+detail.representationId()+"|"+detail.name()+"|"+detail.expression();
            String suffix = sha256(fingerprint).substring(0,16);
            String ruleId = "rule:visual-" + suffix;
            importedRuleIds.add(ruleId);
            if(existingRuleIds.contains(ruleId)) continue;
            String expressionId = "expression:external-" + suffix;
            ExpressionDefinition translated = safeTranslator.translate(expressionId, detail.name(), detail.contextClassId(),
                    detail.expression(), associated, detail.technology());
            boolean executable = translated != null;
            if(existingExpressionIds.add(expressionId)) expressionDefinitions.add(executable ? translated : externalPlaceholder(detail, expressionId));
            Map<String,String> annotations = new LinkedHashMap<>();
            annotations.put("hydra.visual.editorDefinitionId", editor.id());
            annotations.put("hydra.visual.representationId", detail.representationId());
            annotations.put("hydra.import.sourceExpression", detail.expression());
            annotations.put("hydra.import.reviewRequired", Boolean.toString(!executable));
            annotations.put("hydra.import.safeTranslation", Boolean.toString(executable));
            annotations.put("hydra.import.association", "visual-representation");
            annotations.put("hydra.import.technology", detail.technology());
            if(!detail.contextClassId().isBlank()) annotations.put("hydra.import.targetClassId",detail.contextClassId());
            ValidationContext context = detail.contextClassId().isBlank()
                    ? new ValidationContext(ValidationContextKind.REPRESENTATION, "", "")
                    : new ValidationContext(ValidationContextKind.CLASS, detail.contextClassId(), "");
            rules.add(new ValidationRule(ruleId, detail.name(), "VISUAL_"+suffix.toUpperCase(Locale.ROOT),
                    executable
                            ? "Imported from a visual representation and translated deterministically to the neutral validation runtime."
                            : "Imported from a visual representation. The original expression is preserved and remains associated with the representation for review.",
                    executable, context, severity(detail.severity()), expressionId,
                    diagnosticMessage(detail), executable ? List.of(ValidationTrigger.ON_OPEN, ValidationTrigger.ON_ATTRIBUTE_CHANGE,
                            ValidationTrigger.ON_RELATION_CHANGE, ValidationTrigger.MANUAL, ValidationTrigger.FULL_VALIDATION) : List.of(),
                    List.of(), List.of(), executable ? ValidationExecutionMode.INCREMENTAL : ValidationExecutionMode.EXTERNAL,
                    authoritative(detail), executable ? ConversionStatus.FULL : ConversionStatus.EXTERNAL,
                    new ValidationProvenance(detail.technology(), detail.source(), detail.name(), sha256(detail.originalFragment()), importedAt, detail.originalFragment()), annotations));
            existingRuleIds.add(ruleId);
        }

        ExpressionModel updatedExpressions = new ExpressionModel(ExpressionModel.CURRENT_SCHEMA_VERSION, associated.id(), associated.id(),
                expressionDefinitions, merge(expressionState.model().annotations(), Map.of("hydra.import.visualValidations", "true")));
        if(expressionState.etag().isBlank()) expressions.create(associated.id(), updatedExpressions);
        else expressions.replace(associated.id(), updatedExpressions, expressionState.etag());

        String sourceHash = sha256(details.stream().map(v -> v.source()+"|"+v.name()+"|"+v.expression()).reduce("", (a,b)->a+"\n"+b));
        String sourceId = "source:visual-" + sourceHash.substring(0,16);
        List<ImportedValidationSource> sources = new ArrayList<>(state.model().importedSources());
        if(sources.stream().noneMatch(source -> source.id().equals(sourceId))) {
            long executableCount = rules.stream().filter(rule -> importedRuleIds.contains(rule.id()) && rule.enabled()).count();
            ConversionStatus sourceConversion = executableCount == 0 ? ConversionStatus.EXTERNAL
                    : executableCount == importedRuleIds.size() ? ConversionStatus.FULL : ConversionStatus.PARTIAL;
            sources.add(new ImportedValidationSource(sourceId, details.get(0).technology(), details.get(0).source(), sourceHash, sourceConversion,
                    importedAt, Map.of("hydra.visual.editorDefinitionId", editor.id(), "hydra.visual.representationIds",
                            String.join(",", details.stream().map(VisualImportedValidation::representationId).filter(v->!v.isBlank()).distinct().toList()))));
        }
        String setId = "ruleset:visual-" + sha256(editor.id()).substring(0,16);
        List<ValidationRuleSet> sets = new ArrayList<>(); boolean replaced = false;
        for(ValidationRuleSet set : state.model().ruleSets()) {
            if(set.id().equals(setId)) { sets.add(new ValidationRuleSet(setId, "Imported from " + editor.name(), "Validations recovered from the associated visual representation.", true, set.order(), importedRuleIds, Map.of("hydra.visual.editorDefinitionId",editor.id()))); replaced=true; }
            else sets.add(set);
        }
        if(!replaced) sets.add(new ValidationRuleSet(setId, "Imported from " + editor.name(), "Validations recovered from the associated visual representation.", true, sets.size(), importedRuleIds, Map.of("hydra.visual.editorDefinitionId",editor.id())));
        ValidationModel updated = new ValidationModel(ValidationModel.CURRENT_SCHEMA_VERSION, state.model().id(), associated.id(), associated.id(), state.model().actionModelId(), sets, rules, sources, state.model().annotations());
        if(state.etag().isBlank()) validations.create(associated.id(), updated); else validations.replace(associated.id(), updated, state.etag());
        EditorDefinitionModel linked = linkRepresentations(editor, details, importedRuleIds);
        return new VisualImportCommitResult(linked, associated.id(), importedRuleIds.size(), importedRuleIds);
    }

    /** Descripción: Valida las condiciones necesarias para «ensureMetamodel» y rechaza entradas no válidas. Entradas: requested: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel ensureMetamodel(MetaModel requested) {
        try {
            MetaModel existing = metamodels.findById(requested.id()).metamodel();
            if(!existing.equals(requested)) throw new IllegalArgumentException("A metamodel with id '"+requested.id()+"' already exists in HYDRA but does not match the metamodel analysed with the visual representation. Choose that existing metamodel explicitly or import the external metamodel with a different id.");
            return existing;
        } catch(MetamodelApplicationException exception) {
            if(exception.getReason() != MetamodelApplicationException.Reason.NOT_FOUND) throw exception;
            return metamodels.create(requested).metamodel();
        }
    }

    /** Carga el modelo de expresiones o prepara uno vacío conservando el ETag para una actualización transaccional. */
    /** Descripción: Obtiene la información correspondiente a «loadExpressionState» dentro de VisualValidationAssociationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionState loadExpressionState(String metamodelId) {
        try { var loaded = expressions.find(metamodelId); return new ExpressionState(loaded.expressionModel(), loaded.etag()); }
        catch(ExpressionModelApplicationException exception) {
            if(exception.getReason()!=ExpressionModelApplicationException.Reason.NOT_FOUND) throw exception;
            return new ExpressionState(new ExpressionModel(ExpressionModel.CURRENT_SCHEMA_VERSION, metamodelId, metamodelId, List.of(),
                    Map.of("hydra.import.visualValidations","true")), "");
        }
    }

    /** Descripción: Obtiene la información correspondiente a «loadValidationState» dentro de VisualValidationAssociationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ValidationState loadValidationState(String metamodelId) {
        try { var loaded=validations.find(metamodelId); return new ValidationState(loaded.validationModel(),loaded.etag()); }
        catch(ValidationModelApplicationException exception) {
            if(exception.getReason()!=ValidationModelApplicationException.Reason.NOT_FOUND) throw exception;
            return new ValidationState(new ValidationModel(ValidationModel.CURRENT_SCHEMA_VERSION,validationDocumentId(metamodelId),metamodelId,metamodelId,"",List.of(),List.of(),List.of(),Map.of()),"");
        }
    }

    /** Descripción: Deriva el identificador de documento del modelo de validaciones sin usar ':' (reservado a ids de elemento) y respetando el máximo de 120 caracteres. Entradas: metamodelId: identificador del metamodelo propietario. Salida: identificador de documento estable «&lt;id&gt;.validations». */
    /** Descripción: Ejecuta la operación «validationDocumentId» correspondiente a la responsabilidad de VisualValidationAssociationService. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private static String validationDocumentId(String metamodelId) {
        String suffix = ".validations";
        String base = metamodelId.length() + suffix.length() <= 120 ? metamodelId : metamodelId.substring(0, 120 - suffix.length());
        return base + suffix;
    }

    /** Descripción: Ejecuta la operación «linkRepresentations» correspondiente a la responsabilidad de VisualValidationAssociationService. Entradas: editor: dato de entrada necesario para la operación; details: dato de entrada necesario para la operación; ruleIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private EditorDefinitionModel linkRepresentations(EditorDefinitionModel editor, List<VisualImportedValidation> details, List<String> ruleIds) {
        Map<String,List<String>> byRepresentation = new LinkedHashMap<>();
        for(int i=0;i<details.size() && i<ruleIds.size();i++) if(!details.get(i).representationId().isBlank()) byRepresentation.computeIfAbsent(details.get(i).representationId(), ignored->new ArrayList<>()).add(ruleIds.get(i));
        List<RepresentationDefinition> representations = editor.representations().stream().map(rep -> {
            List<String> ids = byRepresentation.get(rep.id()); if(ids==null||ids.isEmpty()) return rep;
            Map<String,String> annotations = new LinkedHashMap<>(rep.annotations()); annotations.put("hydra.validation.ruleIds", String.join(",",ids)); annotations.put("hydra.validation.source",details.stream().filter(detail->detail.representationId().equals(rep.id())).map(VisualImportedValidation::technology).findFirst().orElse("EXTERNAL"));
            return new RepresentationDefinition(rep.id(),rep.name(),rep.kind(),rep.rootClassIds(),rep.nodeMappings(),rep.edgeMappings(),rep.toolIds(),rep.layers(),rep.filters(),rep.editable(),annotations);
        }).toList();
        Map<String,String> annotations = new LinkedHashMap<>(editor.annotations()); annotations.put("hydra.validation.ruleIds",String.join(",",ruleIds));
        return new EditorDefinitionModel(editor.schemaVersion(),editor.id(),editor.metamodelId(),editor.name(),representations,editor.palette(),editor.forms(),editor.capabilityManifest(),editor.runtimePolicy(),editor.codeGeneration(),annotations);
    }

    /** Conserva una expresión externa no traducible como placeholder no ejecutable para evitar referencias rotas en el modelo canónico. */
    /** Descripción: Ejecuta la operación «externalPlaceholder» correspondiente a la responsabilidad de VisualValidationAssociationService. Entradas: detail: dato de entrada necesario para la operación; expressionId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionDefinition externalPlaceholder(VisualImportedValidation detail, String expressionId) {
        return new ExpressionDefinition(expressionId, detail.name() + " · external condition",
                "The original external condition is preserved for review and is never executed by this placeholder.",
                detail.contextClassId(), ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN),
                new LiteralExpressionNode("node:" + sha256(expressionId).substring(0,16),
                        ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN), true),
                Map.of("hydra.import.reviewOnly","true", "hydra.import.originalCondition",detail.expression(),
                        "hydra.import.technology",detail.technology()));
    }

    /** Evita mostrar código AQL/OCL como si fuera un mensaje final cuando el texto requiere evaluación externa. */
    /** Descripción: Ejecuta la operación «diagnosticMessage» correspondiente a la responsabilidad de VisualValidationAssociationService. Entradas: detail: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String diagnosticMessage(VisualImportedValidation detail) {
        String message = detail.message() == null ? "" : detail.message().trim();
        if(message.isBlank()) return "Validation '" + detail.name() + "' failed.";
        if(message.regionMatches(true,0,"aql:",0,4) || message.regionMatches(true,0,"ocl:",0,4)) {
            String body = message.substring(4).trim();
            if((body.startsWith("'") && body.endsWith("'") && body.indexOf("' +") < 0)
                    || (body.startsWith("\"") && body.endsWith("\"") && body.indexOf("\" +") < 0)) {
                return body.substring(1, body.length()-1);
            }
            return "Validation '" + detail.name() + "' failed.";
        }
        return message;
    }

    /** Descripción: Ejecuta la operación «authoritative» correspondiente a la responsabilidad de VisualValidationAssociationService. Entradas: detail: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private AuthoritativeRepresentation authoritative(VisualImportedValidation detail) {
        String technology=detail.technology().toUpperCase(Locale.ROOT);
        if(technology.contains("SIRIUS")) return AuthoritativeRepresentation.SIRIUS_AQL;
        if(technology.contains("GMF") && detail.expression().toLowerCase(Locale.ROOT).startsWith("ocl:")) return AuthoritativeRepresentation.OCL;
        return AuthoritativeRepresentation.EXTERNAL;
    }

    /** Descripción: Ejecuta la operación «severity» correspondiente a la responsabilidad de VisualValidationAssociationService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private ValidationSeverity severity(String value) { if("ERROR".equalsIgnoreCase(value)) return ValidationSeverity.ERROR; if("INFO".equalsIgnoreCase(value)) return ValidationSeverity.INFO; return ValidationSeverity.WARNING; }
    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de VisualValidationAssociationService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(String value) { try { byte[] digest=MessageDigest.getInstance("SHA-256").digest((value==null?"":value).getBytes(StandardCharsets.UTF_8)); return java.util.HexFormat.of().formatHex(digest); } catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    /** Descripción: Actualiza el estado gestionado por VisualValidationAssociationService mediante la operación «merge». Entradas: left: dato de entrada necesario para la operación; right: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,String> merge(Map<String,String> left, Map<String,String> right) { Map<String,String> result=new LinkedHashMap<>(); if(left!=null)result.putAll(left); if(right!=null)result.putAll(right); return Map.copyOf(result); }
    private record ExpressionState(ExpressionModel model, String etag) {}
    private record ValidationState(ValidationModel model, String etag) {}
}
