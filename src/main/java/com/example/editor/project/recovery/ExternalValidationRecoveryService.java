package com.example.editor.project.recovery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.validation.action.ActionDefinition;
import com.example.editor.validation.action.ActionModel;
import com.example.editor.validation.action.ActionModelApplicationException;
import com.example.editor.validation.action.ActionModelApplicationService;
import com.example.editor.validation.action.ActionOperation;
import com.example.editor.validation.action.ActionSelector;
import com.example.editor.validation.action.ActionSelectorKind;
import com.example.editor.validation.action.ActionTransactionPolicy;
import com.example.editor.validation.action.ActionUndoPolicy;
import com.example.editor.validation.action.ActionValueSource;
import com.example.editor.validation.action.SetAttributeOperation;
import com.example.editor.semantic.expression.ExpressionDefinition;
import com.example.editor.validation.expression.ExpressionModel;
import com.example.editor.validation.expression.ExpressionModelApplicationException;
import com.example.editor.validation.expression.ExpressionModelApplicationService;
import com.example.editor.semantic.expression.ExpressionValueKind;
import com.example.editor.semantic.expression.ExpressionValueType;
import com.example.editor.semantic.expression.LiteralExpressionNode;
import com.example.editor.validation.importing.SafeExternalConstraintTranslator;
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

/**
 * Recupera validaciones externas sin ejecutar código del proyecto importado.
 * Las expresiones que no pueden convertirse de forma inequívoca se conservan como reglas EXTERNAL deshabilitadas.
 * Los fixes sin traducción segura se conservan como acciones review-only, también deshabilitadas, para no inventar semántica.
 */
@Service
public class ExternalValidationRecoveryService {
    private static final Pattern CONTEXT = Pattern.compile("(?i)\\bcontext\\s+([A-Za-z_][\\w!:]*)\\s*\\{");
    private static final Pattern RULE = Pattern.compile("(?i)\\b(constraint|critique)\\s+([A-Za-z_][\\w-]*)\\s*\\{");
    private static final Pattern FIX = Pattern.compile("(?i)\\bfix(?:\\s+([A-Za-z_][\\w-]*))?\\s*\\{");
    private static final Pattern DO = Pattern.compile("(?i)\\bdo\\s*\\{");
    private static final Pattern SIMPLE_ASSIGN = Pattern.compile("(?s)^\\s*self\\.([A-Za-z_][\\w]*)\\s*:=\\s*(.*?)\\s*;?\\s*$");
    private static final Pattern OCL_CONTEXT = Pattern.compile("(?is)\\bcontext\\s+([A-Za-z_][\\w!:]*)\\s+inv(?:\\s+([A-Za-z_][\\w-]*))?\\s*:\\s*([^\\r\\n]+(?:\\r?\\n(?!\\s*context\\b)[^\\r\\n]+)*)");
    private static final Pattern JAVA_CHECK = Pattern.compile("(?s)@Check\\b[^\\n]*\\n\\s*(?:public|protected|private)?\\s*void\\s+([A-Za-z_][\\w]*)\\s*\\(");
    private static final Pattern JAVA_FIX = Pattern.compile("(?s)@Fix\\s*\\(([^)]*)\\)[^\\n]*\\n\\s*(?:public|protected|private)?\\s*void\\s+([A-Za-z_][\\w]*)\\s*\\(");
    private static final Pattern SIRIUS_RULE = Pattern.compile("(?is)<ownedRules\\b([^>]*)>(.*?)</ownedRules>");
    private static final Pattern XML_NAME = Pattern.compile("(?is)\\bname\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern XML_TARGET_CLASS = Pattern.compile("(?is)\\btargetClass\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern XML_FEATURE_NAME = Pattern.compile("(?is)\\bfeatureName\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern XML_VALUE_EXPRESSION = Pattern.compile("(?is)\\bvalueExpression\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern SIRIUS_FIX = Pattern.compile("(?is)<fixes\\b([^>]*)>(.*?)</fixes>");
    private static final Pattern SIRIUS_SET_VALUE = Pattern.compile("(?is)<[^>]*\\bxsi:type\\s*=\\s*[\"']tool:SetValue[\"'][^>]*>");
    private static final Pattern LANGIUM_CHECK = Pattern.compile("(?m)(?:public\\s+)?((?:check|validate)[A-Za-z0-9_]*)\\s*\\(\\s*[A-Za-z_$][\\w$]*\\s*:\\s*([A-Za-z_$][\\w$]*)\\s*,\\s*[A-Za-z_$][\\w$]*\\s*:\\s*ValidationAcceptor[^)]*\\)\\s*(?::[^\\{]+)?\\{");
    private static final Pattern LANGIUM_CODE = Pattern.compile("(?i)\\bcode\\s*:[\\s]*[\\\"']([^\\\"']+)[\\\"']");
    private static final Pattern PY_CHECK = Pattern.compile("(?m)^\\s*def\\s+((?:validate|check)_[A-Za-z0-9_]+)\\s*\\(([^)]*)\\)\\s*(?:->[^:]*)?:");
    private static final Pattern PY_FIX = Pattern.compile("(?m)^\\s*def\\s+((?:fix|quickfix|repair)_[A-Za-z0-9_]+)\\s*\\(([^)]*)\\)\\s*(?:->[^:]*)?:");
    private static final Pattern PY_ANNOTATED_TYPE = Pattern.compile(":\\s*([A-Z][A-Za-z0-9_]*)");

    private final ExpressionModelApplicationService expressions;
    private final ActionModelApplicationService actions;
    private final ValidationModelApplicationService validations;
    private final SafeExternalConstraintTranslator safeTranslator;

    /** Descripción: Inicializa una instancia de ExternalValidationRecoveryService con sus dependencias y datos de entrada. Entradas: expressions: dato de entrada necesario para la operación; actions: dato de entrada necesario para la operación; validations: dato de entrada necesario para la operación; safeTranslator: traductor declarativo que nunca ejecuta código importado. Salida: deja la instancia inicializada y lista para su uso. */ public ExternalValidationRecoveryService(ExpressionModelApplicationService expressions,
            ActionModelApplicationService actions, ValidationModelApplicationService validations,
            SafeExternalConstraintTranslator safeTranslator) {
        this.expressions = expressions;
        this.actions = actions;
        this.validations = validations;
        this.safeTranslator = safeTranslator;
    }

    /** Descripción: Ejecuta la operación «recover» correspondiente a la responsabilidad de ExpressionState. Entradas: metamodelId: identificador que delimita el recurso objetivo; metamodel: dato de entrada necesario para la operación; entries: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ ExternalValidationRecoveryResult recover(String metamodelId, MetaModel metamodel, List<ExternalProjectArchiveEntry> entries) {
        List<RecoveredRule> recoveredRules = new ArrayList<>();
        List<RecoveredFix> recoveredFixes = new ArrayList<>();
        List<ExpressionDefinition> recoveredActionExpressions = new ArrayList<>();
        List<ImportedValidationSource> importedSources = new ArrayList<>();
        List<String> sourcePaths = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        Instant now = Instant.now();

        for(ExternalProjectArchiveEntry entry : entries) {
            String lower = entry.path().toLowerCase(Locale.ROOT);
            if(!potentialValidationSource(lower)) continue;
            var inspected = StaticSourceInspectionPolicy.inspectText(entry);
            if(inspected.isEmpty()) {
                if(entry.bytes() != null && entry.bytes().length > StaticSourceInspectionPolicy.MAX_TEXT_SOURCE_BYTES)
                    diagnostics.add("Skipped static validation inspection of '" + entry.path() + "' because it exceeds the "
                            + StaticSourceInspectionPolicy.MAX_TEXT_SOURCE_BYTES + " byte source limit. The original resource remains preserved.");
                continue;
            }
            String text = inspected.get();
            if(lower.endsWith(".evl")) {
                parseEvl(entry.path(), text, metamodel, recoveredRules, recoveredFixes, diagnostics);
                importedSources.add(source(entry, "EVL", now, ConversionStatus.EXTERNAL));
                sourcePaths.add(entry.path());
            } else if(lower.endsWith(".ocl")) {
                parseOcl(entry.path(), text, metamodel, recoveredRules, diagnostics);
                importedSources.add(source(entry, "OCL", now, ConversionStatus.EXTERNAL));
                sourcePaths.add(entry.path());
            } else if(lower.endsWith(".odesign")) {
                parseSiriusFixes(entry.path(), text, metamodel, recoveredFixes, recoveredActionExpressions, diagnostics);
                if(recoveredFixes.stream().anyMatch(value -> "SIRIUS_DESKTOP".equals(value.action().annotations().get("hydra.import.format")))) {
                    importedSources.add(source(entry, "SIRIUS_DESKTOP", now, ConversionStatus.EXTERNAL));
                    sourcePaths.add(entry.path());
                }
            } else if(lower.endsWith(".java")) {
                boolean recognized = false;
                if(isXtextValidator(lower, text)) { parseJavaChecks(entry.path(), text, metamodel, recoveredRules, diagnostics); recognized = true; }
                if(isXtextQuickFix(lower, text)) { parseJavaFixes(entry.path(), text, recoveredFixes, diagnostics); recognized = true; }
                if(recognized) { importedSources.add(source(entry, "XTEXT_JAVA", now, ConversionStatus.EXTERNAL)); sourcePaths.add(entry.path()); }
            } else if((lower.endsWith(".ts") || lower.endsWith(".tsx")) && isLangiumSource(lower, text)) {
                int beforeRules = recoveredRules.size(), beforeFixes = recoveredFixes.size();
                parseLangiumChecks(entry.path(), text, metamodel, recoveredRules, diagnostics);
                parseLangiumCodeActions(entry.path(), text, recoveredFixes, diagnostics);
                if(recoveredRules.size() > beforeRules || recoveredFixes.size() > beforeFixes) {
                    importedSources.add(source(entry, "LANGIUM_TYPESCRIPT", now, ConversionStatus.EXTERNAL)); sourcePaths.add(entry.path());
                }
            } else if(lower.endsWith(".py") && isBesserSource(lower, text)) {
                int beforeRules = recoveredRules.size(), beforeFixes = recoveredFixes.size();
                parseBesserChecks(entry.path(), text, metamodel, recoveredRules, diagnostics);
                parseBesserFixes(entry.path(), text, recoveredFixes, diagnostics);
                if(recoveredRules.size() > beforeRules || recoveredFixes.size() > beforeFixes) {
                    importedSources.add(source(entry, "BESSER_PYTHON", now, ConversionStatus.EXTERNAL)); sourcePaths.add(entry.path());
                }
            }
        }
        if(recoveredRules.isEmpty() && recoveredFixes.isEmpty()) {
            return diagnostics.isEmpty() ? ExternalValidationRecoveryResult.empty()
                    : new ExternalValidationRecoveryResult(0, 0, 0, sourcePaths, diagnostics);
        }

        ExpressionState expressionState = expressionState(metamodelId);
        List<ExpressionDefinition> expressionDefinitions = new ArrayList<>(expressionState.model().expressions());
        Set<String> expressionIds = new LinkedHashSet<>();
        expressionDefinitions.forEach(value -> expressionIds.add(value.id()));
        for(ExpressionDefinition recoveredExpression : recoveredActionExpressions) {
            if(expressionIds.add(recoveredExpression.id())) expressionDefinitions.add(recoveredExpression);
        }
        Map<String,Boolean> executableRules = new LinkedHashMap<>();
        for(RecoveredRule rule : recoveredRules) {
            ExpressionDefinition translated = rule.guard().isBlank()
                    ? safeTranslator.translate(rule.expressionId(), rule.name(), rule.contextClassId(), rule.condition(), metamodel, rule.technology())
                    : null;
            boolean executable = translated != null;
            executableRules.put(rule.ruleId(), executable);
            if(expressionIds.add(rule.expressionId())) expressionDefinitions.add(executable ? translated : placeholderExpression(rule));
        }
        ExpressionModel expressionModel = new ExpressionModel(ExpressionModel.CURRENT_SCHEMA_VERSION, metamodelId, metamodelId,
                expressionDefinitions, merge(expressionState.model().annotations(), Map.of("hydra.import.externalProject", "true")));
        if(expressionState.etag().isBlank()) expressions.create(metamodelId, expressionModel);
        else expressions.replace(metamodelId, expressionModel, expressionState.etag());

        Map<String, List<RecoveredFix>> fixesByRule = new LinkedHashMap<>();
        for(RecoveredFix fix : recoveredFixes) fixesByRule.computeIfAbsent(fix.ruleName().toLowerCase(Locale.ROOT), ignored -> new ArrayList<>()).add(fix);
        ActionState actionState = actionState(metamodelId);
        List<ActionDefinition> actionDefinitions = new ArrayList<>(actionState.model().actions());
        Set<String> actionIds = new LinkedHashSet<>(); actionDefinitions.forEach(value -> actionIds.add(value.id()));
        int reviewOnly = 0;
        for(RecoveredFix fix : recoveredFixes) {
            if(actionIds.add(fix.action().id())) actionDefinitions.add(fix.action());
            if("true".equals(fix.action().annotations().get("hydra.import.reviewOnly"))) reviewOnly++;
        }
        String actionModelId = actionDefinitions.isEmpty() ? "" : (actionState.model().id().isBlank() ? documentId(metamodelId, "actions") : actionState.model().id());
        if(!actionDefinitions.isEmpty()) {
            ActionModel actionModel = new ActionModel(ActionModel.CURRENT_SCHEMA_VERSION, actionModelId, metamodelId, metamodelId,
                    actionDefinitions, merge(actionState.model().annotations(), Map.of("hydra.import.externalProject", "true")));
            if(actionState.etag().isBlank()) actions.create(metamodelId, actionModel);
            else actions.replace(metamodelId, actionModel, actionState.etag());
        }

        ValidationState validationState = validationState(metamodelId);
        List<ValidationRule> validationRules = new ArrayList<>();
        for(ValidationRule existing : validationState.model().rules()) {
            List<String> importedFixIds = fixesByRule.getOrDefault(existing.name().toLowerCase(Locale.ROOT), List.of()).stream().map(value -> value.action().id()).toList();
            if(importedFixIds.isEmpty()) validationRules.add(existing);
            else {
                List<String> mergedFixIds = new ArrayList<>(existing.quickFixIds()); importedFixIds.stream().filter(id -> !mergedFixIds.contains(id)).forEach(mergedFixIds::add);
                Map<String,String> linkedAnnotations = new LinkedHashMap<>(existing.annotations()); linkedAnnotations.put("hydra.import.externalQuickFixesLinked","true");
                validationRules.add(new ValidationRule(existing.id(),existing.name(),existing.code(),existing.description(),existing.enabled(),existing.context(),existing.severity(),existing.conditionExpressionId(),existing.messageTemplate(),existing.triggers(),existing.dependencies(),mergedFixIds,existing.executionMode(),existing.authoritativeRepresentation(),existing.conversionStatus(),existing.provenance(),linkedAnnotations));
            }
        }
        Set<String> validationIds = new LinkedHashSet<>(); validationRules.forEach(value -> validationIds.add(value.id()));
        List<String> importedRuleIds = new ArrayList<>();
        for(RecoveredRule recovered : recoveredRules) {
            ValidationRule nativeEquivalent = validationRules.stream()
                    .filter(existing -> sameNativeVisualRule(existing, recovered)).findFirst().orElse(null);
            if(nativeEquivalent != null) {
                importedRuleIds.add(nativeEquivalent.id());
                diagnostics.add("Reused native visual validation '" + nativeEquivalent.name()
                        + "' while attaching recovered quick fixes instead of creating a duplicate rule.");
                continue;
            }
            importedRuleIds.add(recovered.ruleId());
            if(!validationIds.add(recovered.ruleId())) continue;
            List<String> fixIds = linkedFixes(fixesByRule, recovered.name(), recovered.code()).stream()
                    .map(value -> value.action().id()).distinct().toList();
            boolean executable = executableRules.getOrDefault(recovered.ruleId(), false);
            Map<String,String> annotations = new LinkedHashMap<>();
            annotations.put("hydra.import.format", recovered.technology());
            annotations.put("hydra.import.originalCondition", recovered.condition());
            annotations.put("hydra.import.reviewRequired", Boolean.toString(!executable));
            annotations.put("hydra.import.safeTranslation", Boolean.toString(executable));
            if(!recovered.guard().isBlank()) annotations.put("hydra.import.originalGuard", recovered.guard());
            validationRules.add(new ValidationRule(recovered.ruleId(), recovered.name(), recovered.code(),
                    executable
                            ? "Recovered from " + recovered.technology() + " source and translated deterministically to the neutral validation runtime."
                            : "Recovered from " + recovered.technology() + " source. The original condition is preserved verbatim and remains external until it is mapped safely.",
                    executable, new ValidationContext(recovered.contextClassId().isBlank() ? ValidationContextKind.MODEL : ValidationContextKind.CLASS, recovered.contextClassId(), ""), recovered.severity(),
                    recovered.expressionId(), recovered.message(), executable
                            ? List.of(ValidationTrigger.ON_OPEN, ValidationTrigger.ON_ATTRIBUTE_CHANGE, ValidationTrigger.ON_RELATION_CHANGE,
                                    ValidationTrigger.ON_SAVE, ValidationTrigger.MANUAL, ValidationTrigger.FULL_VALIDATION)
                            : List.of(ValidationTrigger.ON_SAVE, ValidationTrigger.MANUAL),
                    List.of(), fixIds, executable ? ValidationExecutionMode.INCREMENTAL : ValidationExecutionMode.EXTERNAL,
                    recovered.authoritativeRepresentation(), executable ? ConversionStatus.FULL : ConversionStatus.EXTERNAL,
                    new ValidationProvenance(recovered.technology(), recovered.sourcePath(), recovered.name(), sha256(recovered.originalFragment()), now, recovered.originalFragment()), annotations));
        }
        List<ValidationRuleSet> sets = new ArrayList<>(validationState.model().ruleSets());
        if(!importedRuleIds.isEmpty()) {
            String setId = "ruleset:external-project-" + sha256(String.join("|", sourcePaths)).substring(0, 16);
            sets.removeIf(value -> value.id().equals(setId));
            sets.add(new ValidationRuleSet(setId, "External project validations", "Rules recovered from imported project validation technologies.", true,
                    sets.size(), importedRuleIds, Map.of("hydra.import.externalProject", "true")));
        }
        List<ImportedValidationSource> sources = new ArrayList<>(validationState.model().importedSources());
        Set<String> sourceIds = new LinkedHashSet<>(); sources.forEach(value -> sourceIds.add(value.id()));
        importedSources.stream().filter(value -> sourceIds.add(value.id())).forEach(sources::add);
        String effectiveActionModelId = actionDefinitions.isEmpty() ? validationState.model().actionModelId() : actionModelId;
        ValidationModel validationModel = new ValidationModel(ValidationModel.CURRENT_SCHEMA_VERSION,
                validationState.model().id().isBlank() ? documentId(metamodelId, "validations") : validationState.model().id(),
                metamodelId, metamodelId, effectiveActionModelId, sets, validationRules, sources,
                merge(validationState.model().annotations(), Map.of("hydra.import.externalProject", "true")));
        if(validationState.etag().isBlank()) validations.create(metamodelId, validationModel);
        else validations.replace(metamodelId, validationModel, validationState.etag());

        return new ExternalValidationRecoveryResult(recoveredRules.size(), recoveredFixes.size(), reviewOnly,
                sourcePaths, diagnostics);
    }

    /** Detecta la misma regla Sirius ya importada por el adaptador visual para no duplicarla al recuperar fixes. */
    /** Descripción: Ejecuta la operación «sameNativeVisualRule» correspondiente a la responsabilidad de ValidationState. Entradas: existing: dato de entrada necesario para la operación; recovered: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean sameNativeVisualRule(ValidationRule existing, RecoveredRule recovered) {
        if(existing == null || recovered == null || !existing.name().equalsIgnoreCase(recovered.name())) return false;
        if(!"SIRIUS_DESKTOP".equalsIgnoreCase(recovered.technology())) return false;
        String recoveredContext = recovered.contextClassId() == null ? "" : recovered.contextClassId().trim();
        String existingContext = existing.context() == null || existing.context().classId() == null ? "" : existing.context().classId().trim();
        if(!recoveredContext.isBlank() && !existingContext.isBlank() && !recoveredContext.equals(existingContext)) return false;
        String technology = existing.provenance() == null ? "" : existing.provenance().technology();
        if(technology.toUpperCase(Locale.ROOT).contains("SIRIUS")) return true;
        return existing.annotations().getOrDefault("hydra.import.technology", "").toUpperCase(Locale.ROOT).contains("SIRIUS");
    }

    /** Descripción: Ejecuta la operación «parseEvl» correspondiente a la responsabilidad de ExpressionState. Entradas: path: ruta o ubicación utilizada por la operación; source: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; rules: dato de entrada necesario para la operación; fixes: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseEvl(String path, String source, MetaModel metamodel, List<RecoveredRule> rules,
            List<RecoveredFix> fixes, List<String> diagnostics) {
        for(Block context : blocks(source, CONTEXT)) {
            String contextName = context.group(1);
            MetaClass metaClass = resolveClass(metamodel, contextName);
            String classId = metaClass == null ? "" : metaClass.id();
            if(metaClass == null) diagnostics.add("EVL context '" + contextName + "' could not be mapped to a metamodel class; its rules remain external and reviewable.");
            for(Block ruleBlock : blocks(context.body(), RULE)) {
                String kind = ruleBlock.group(1).toLowerCase(Locale.ROOT);
                String name = ruleBlock.group(2);
                String condition = field(ruleBlock.body(), "check");
                String guard = field(ruleBlock.body(), "guard");
                String message = unquote(field(ruleBlock.body(), "message"));
                if(message.isBlank()) message = name;
                String suffix = sha256(path + "|" + contextName + "|" + name).substring(0, 16);
                rules.add(new RecoveredRule("rule:evl-" + suffix, "expression:evl-" + suffix, name,
                        code("EVL_" + name), classId, "EVL", path, condition, guard, message,
                        "critique".equals(kind) ? ValidationSeverity.WARNING : ValidationSeverity.ERROR,
                        AuthoritativeRepresentation.EVL, ruleBlock.full()));
                for(Block fixBlock : blocks(ruleBlock.body(), FIX)) {
                    String title = unquote(field(fixBlock.body(), "title"));
                    if(title.isBlank()) title = fixBlock.group(1) == null ? "Repair " + name : fixBlock.group(1);
                    Block doBlock = blocks(fixBlock.body(), DO).stream().findFirst().orElse(null);
                    String body = doBlock == null ? "" : doBlock.body().trim();
                    fixes.add(new RecoveredFix(name, action(path, contextName, classId, name, title, body, metamodel)));
                }
            }
        }
    }

    /** Recupera quick fixes Sirius de forma estática. Solo SetValue inequívoco se convierte a operaciones canónicas; el resto permanece review-only. */
    /** Descripción: Ejecuta la operación «parseSiriusFixes» correspondiente a la responsabilidad de UnsupportedLiteral. Entradas: path: ruta o ubicación utilizada por la operación; source: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; fixes: dato de entrada necesario para la operación; actionExpressions: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseSiriusFixes(String path, String source, MetaModel metamodel, List<RecoveredFix> fixes,
            List<ExpressionDefinition> actionExpressions, List<String> diagnostics) {
        Matcher rules = SIRIUS_RULE.matcher(source); int recovered = 0;
        while(rules.find()) {
            String ruleName = xmlAttribute(rules.group(1), XML_NAME); if(ruleName.isBlank()) continue;
            String externalClass = xmlAttribute(rules.group(1), XML_TARGET_CLASS);
            MetaClass context = externalClass.isBlank() ? null : resolveClass(metamodel, externalClass);
            String contextClassId = context == null ? "" : context.id();
            Matcher fixMatcher = SIRIUS_FIX.matcher(rules.group(2));
            while(fixMatcher.find()) {
                String title = xmlAttribute(fixMatcher.group(1), XML_NAME); if(title.isBlank()) title = "Imported Sirius repair for " + ruleName;
                String body = fixMatcher.group(2).trim();
                String id = "action:sirius-" + sha256(path + "|" + ruleName + "|" + title + "|" + body).substring(0,16);
                List<ActionOperation> operations = new ArrayList<>();
                List<ExpressionDefinition> localExpressions = new ArrayList<>();
                boolean safe = context != null;
                Matcher setValues = SIRIUS_SET_VALUE.matcher(body); int setValueCount = 0;
                while(setValues.find()) {
                    setValueCount++;
                    String tag = setValues.group();
                    String featureName = xmlAttribute(tag, XML_FEATURE_NAME);
                    String externalValue = xmlUnescape(xmlAttribute(tag, XML_VALUE_EXPRESSION));
                    MetaAttribute attribute = resolveAttribute(metamodel, contextClassId, featureName);
                    if(attribute == null || externalValue.isBlank()) { safe = false; break; }
                    String normalizedValue = stripExternalPrefix(externalValue);
                    Object literalValue = literal(normalizedValue);
                    ActionValueSource valueSource;
                    if(literalValue != UnsupportedLiteral.INSTANCE) valueSource = ActionValueSource.literal(literalValue);
                    else {
                        String valueExpressionId = "expression:sirius-fix-" + sha256(id + "|" + attribute.id() + "|" + externalValue).substring(0,16);
                        ExpressionDefinition translated = safeTranslator.translateValue(valueExpressionId, title + " · " + featureName,
                                contextClassId, externalValue, metamodel, "SIRIUS_DESKTOP");
                        if(translated == null) { safe = false; break; }
                        localExpressions.add(translated);
                        valueSource = ActionValueSource.expression(valueExpressionId);
                    }
                    operations.add(new SetAttributeOperation("operation:" + sha256(id + "|" + attribute.id() + "|" + setValueCount).substring(0,16),
                            new ActionSelector(ActionSelectorKind.SELF,"",""), attribute.id(), valueSource));
                }
                if(setValueCount == 0) safe = false;
                if(!safe) { operations = List.of(); localExpressions = List.of(); }
                else actionExpressions.addAll(localExpressions);
                Map<String,String> annotations = new LinkedHashMap<>();
                annotations.put("hydra.import.format","SIRIUS_DESKTOP"); annotations.put("hydra.import.validationRuleName",ruleName);
                annotations.put("hydra.import.contextClassId",contextClassId);
                annotations.put("hydra.import.reviewOnly",Boolean.toString(!safe)); annotations.put("hydra.import.safeTranslation",Boolean.toString(safe)); annotations.put("hydra.import.originalFix",body);
                ActionDefinition action = new ActionDefinition(id,title,safe
                        ? "Native Sirius repair translated deterministically to declarative HYDRA operations. It is previewable, transactional and undoable."
                        : "Native Sirius repair preserved without executing AQL/model operations. Map it to canonical operations before enabling it.",
                        safe,List.of(),"",operations,safe?ActionTransactionPolicy.ATOMIC:ActionTransactionPolicy.BEST_EFFORT,
                        safe?ActionUndoPolicy.REQUIRED:ActionUndoPolicy.NOT_SUPPORTED,annotations);
                fixes.add(new RecoveredFix(ruleName,action)); recovered++;
            }
        }
        if(source.contains("<fixes") && recovered==0) diagnostics.add("Sirius quick fixes were detected in " + path + " but their owning validation rule could not be resolved safely.");
    }
    /** Descripción: Ejecuta la operación «xmlAttribute» correspondiente a la responsabilidad de ExpressionState. Entradas: attributes: dato de entrada necesario para la operación; pattern: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String xmlAttribute(String attributes, Pattern pattern) { Matcher matcher=pattern.matcher(attributes==null?"":attributes); return matcher.find()?matcher.group(1).trim():""; }
    /** Descripción: Ejecuta la operación «xmlUnescape» correspondiente a la responsabilidad de UnsupportedLiteral. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String xmlUnescape(String value) { return value == null ? "" : value.replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"").replace("&apos;","'").replace("&amp;","&"); }
    /** Descripción: Ejecuta la operación «stripExternalPrefix» correspondiente a la responsabilidad de UnsupportedLiteral. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String stripExternalPrefix(String value) { String result=value==null?"":value.trim(); if(result.regionMatches(true,0,"aql:",0,4)||result.regionMatches(true,0,"ocl:",0,4))result=result.substring(4).trim(); return result; }

    /** Descripción: Ejecuta la operación «parseOcl» correspondiente a la responsabilidad de ExpressionState. Entradas: path: ruta o ubicación utilizada por la operación; source: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; rules: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseOcl(String path, String source, MetaModel metamodel, List<RecoveredRule> rules, List<String> diagnostics) {
        Matcher matcher = OCL_CONTEXT.matcher(source);
        int index = 0;
        while(matcher.find()) {
            String contextName = matcher.group(1);
            MetaClass metaClass = resolveClass(metamodel, contextName);
            if(metaClass == null) diagnostics.add("OCL context '" + contextName + "' could not be mapped to a metamodel class.");
            String name = matcher.group(2) == null || matcher.group(2).isBlank() ? "OclInvariant" + (++index) : matcher.group(2);
            String condition = matcher.group(3).trim();
            String suffix = sha256(path + "|" + contextName + "|" + name).substring(0,16);
            rules.add(new RecoveredRule("rule:ocl-"+suffix,"expression:ocl-"+suffix,name,code("OCL_"+name),
                    metaClass == null ? "" : metaClass.id(),"OCL",path,condition,"",name + " must hold.",ValidationSeverity.ERROR,
                    AuthoritativeRepresentation.OCL,matcher.group()));
        }
    }

    /** Descripción: Ejecuta la operación «parseJavaChecks» correspondiente a la responsabilidad de ExpressionState. Entradas: path: ruta o ubicación utilizada por la operación; source: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; rules: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseJavaChecks(String path, String source, MetaModel metamodel, List<RecoveredRule> rules, List<String> diagnostics) {
        Matcher matcher = JAVA_CHECK.matcher(source);
        while(matcher.find()) {
            String name = matcher.group(1);
            String suffix = sha256(path + "|check|" + name).substring(0,16);
            rules.add(new RecoveredRule("rule:xtext-"+suffix,"expression:xtext-"+suffix,name,code("XTEXT_"+name),"","XTEXT_JAVA",path,
                    "External Java @Check method", "", name + " reported a validation issue.", ValidationSeverity.ERROR,
                    AuthoritativeRepresentation.JAVA, source.substring(matcher.start(), Math.min(source.length(), matcher.end()+240))));
        }
        if(!source.contains("@Check")) diagnostics.add("Validator source " + path + " was preserved but no @Check methods were recognized.");
    }

    /** Descripción: Ejecuta la operación «parseJavaFixes» correspondiente a la responsabilidad de ExpressionState. Entradas: path: ruta o ubicación utilizada por la operación; source: dato de entrada necesario para la operación; fixes: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseJavaFixes(String path, String source, List<RecoveredFix> fixes, List<String> diagnostics) {
        Matcher matcher = JAVA_FIX.matcher(source);
        while(matcher.find()) {
            String issue = matcher.group(1).replaceAll("[\\\"']", "").trim();
            String name = matcher.group(2);
            String id = "action:xtext-" + sha256(path + "|fix|" + name).substring(0,16);
            Map<String,String> annotations = Map.of("hydra.import.format","XTEXT_JAVA","hydra.import.reviewOnly","true",
                    "hydra.import.originalFix", source.substring(matcher.start(), Math.min(source.length(), matcher.end()+320)),
                    "hydra.import.issueCode", issue);
            ActionDefinition action = new ActionDefinition(id,name,"Imported Xtext @Fix method. Map the Java repair body before enabling it.",false,
                    List.of(),"",List.of(),ActionTransactionPolicy.BEST_EFFORT,ActionUndoPolicy.NOT_SUPPORTED,annotations);
            fixes.add(new RecoveredFix(issue, action));
        }
        if(!source.contains("@Fix")) diagnostics.add("Quick-fix source " + path + " was preserved but no @Fix methods were recognized.");
    }

    /** Recupera validadores Langium de TypeScript de forma puramente estática. */
    /** Descripción: Ejecuta la operación «parseLangiumChecks» correspondiente a la responsabilidad de ExpressionState. Entradas: path: ruta o ubicación utilizada por la operación; source: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; rules: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseLangiumChecks(String path, String source, MetaModel metamodel, List<RecoveredRule> rules, List<String> diagnostics) {
        Matcher matcher = LANGIUM_CHECK.matcher(source); int recovered = 0;
        while(matcher.find()) {
            String name = matcher.group(1), contextName = matcher.group(2);
            MetaClass context = resolveClass(metamodel, contextName);
            int open = source.indexOf('{', matcher.end()-1), close = open < 0 ? -1 : matchingBrace(source, open);
            String fragment = close > open ? source.substring(matcher.start(), close+1) : source.substring(matcher.start(), Math.min(source.length(), matcher.end()+500));
            Matcher codeMatcher = LANGIUM_CODE.matcher(fragment); String issueCode = codeMatcher.find() ? codeMatcher.group(1) : code("LANGIUM_"+name);
            String suffix = sha256(path+"|langium|"+name+"|"+contextName).substring(0,16);
            rules.add(new RecoveredRule("rule:langium-"+suffix,"expression:langium-"+suffix,name,issueCode,
                    context == null ? "" : context.id(),"LANGIUM_TYPESCRIPT",path,"External Langium ValidationAcceptor method","",
                    name+" reported a validation issue.",ValidationSeverity.ERROR,AuthoritativeRepresentation.EXTERNAL,fragment));
            if(context == null) diagnostics.add("Langium validator '"+name+"' refers to type '"+contextName+"', which could not be mapped uniquely to the imported metamodel.");
            recovered++;
        }
        if(recovered==0 && (source.contains("ValidationAcceptor") || source.contains("ValidationChecks<"))) diagnostics.add("Langium validation infrastructure was detected in "+path+" but no statically recognizable check method was found; the source remains preserved for review.");
    }

    /** Conserva proveedores de CodeAction de Langium como quick fixes no ejecutables hasta traducción explícita. */
    /** Descripción: Ejecuta la operación «parseLangiumCodeActions» correspondiente a la responsabilidad de ExpressionState. Entradas: path: ruta o ubicación utilizada por la operación; source: dato de entrada necesario para la operación; fixes: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseLangiumCodeActions(String path, String source, List<RecoveredFix> fixes, List<String> diagnostics) {
        if(!(source.contains("CodeAction") || source.toLowerCase(Locale.ROOT).contains("codeactionprovider") || source.toLowerCase(Locale.ROOT).contains("quickfix"))) return;
        LinkedHashSet<String> codes = new LinkedHashSet<>(); Matcher matcher=LANGIUM_CODE.matcher(source); while(matcher.find()) codes.add(matcher.group(1));
        if(codes.isEmpty()) codes.add("LANGIUM_CODE_ACTION");
        for(String issue : codes) {
            String id="action:langium-"+sha256(path+"|"+issue).substring(0,16);
            Map<String,String> annotations=new LinkedHashMap<>(); annotations.put("hydra.import.format","LANGIUM_TYPESCRIPT"); annotations.put("hydra.import.reviewOnly","true"); annotations.put("hydra.import.safeTranslation","false"); annotations.put("hydra.import.issueCode",issue); annotations.put("hydra.import.originalFix",source.length()>6000?source.substring(0,6000)+"…":source);
            fixes.add(new RecoveredFix(issue,new ActionDefinition(id,"Langium repair · "+issue,"Static CodeAction source preserved without executing imported TypeScript. Map it to canonical HYDRA operations before enabling it.",false,List.of(),"",List.of(),ActionTransactionPolicy.BEST_EFFORT,ActionUndoPolicy.NOT_SUPPORTED,annotations)));
        }
    }

    /** Recupera validadores Python de proyectos BESSER sin importar ni ejecutar módulos Python. */
    /** Descripción: Ejecuta la operación «parseBesserChecks» correspondiente a la responsabilidad de ExpressionState. Entradas: path: ruta o ubicación utilizada por la operación; source: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación; rules: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseBesserChecks(String path, String source, MetaModel metamodel, List<RecoveredRule> rules, List<String> diagnostics) {
        Matcher matcher=PY_CHECK.matcher(source); int recovered=0;
        while(matcher.find()) {
            String name=matcher.group(1), params=matcher.group(2); Matcher typeMatcher=PY_ANNOTATED_TYPE.matcher(params); String contextName=typeMatcher.find()?typeMatcher.group(1):"";
            MetaClass context=contextName.isBlank()?null:resolveClass(metamodel,contextName);
            String fragment=source.substring(matcher.start(),Math.min(source.length(),matcher.end()+700)); String suffix=sha256(path+"|besser-check|"+name).substring(0,16);
            rules.add(new RecoveredRule("rule:besser-"+suffix,"expression:besser-"+suffix,name,code("BESSER_"+name),context==null?"":context.id(),"BESSER_PYTHON",path,"External BESSER/Python validator","",name+" reported a validation issue.",ValidationSeverity.ERROR,AuthoritativeRepresentation.EXTERNAL,fragment)); recovered++;
        }
        if(recovered==0 && (source.contains("@validator") || source.contains("@constraint"))) diagnostics.add("BESSER/Python validation decorators were preserved in "+path+" but their executable semantics were not inferred.");
    }

    /** Conserva reparaciones BESSER/Python como acciones review-only; nunca ejecuta el módulo importado. */
    /** Descripción: Ejecuta la operación «parseBesserFixes» correspondiente a la responsabilidad de ExpressionState. Entradas: path: ruta o ubicación utilizada por la operación; source: dato de entrada necesario para la operación; fixes: dato de entrada necesario para la operación; diagnostics: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parseBesserFixes(String path, String source, List<RecoveredFix> fixes, List<String> diagnostics) {
        Matcher matcher=PY_FIX.matcher(source); int recovered=0;
        while(matcher.find()) {
            String name=matcher.group(1), id="action:besser-"+sha256(path+"|besser-fix|"+name).substring(0,16);
            String fragment=source.substring(matcher.start(),Math.min(source.length(),matcher.end()+900));
            Map<String,String> annotations=Map.of("hydra.import.format","BESSER_PYTHON","hydra.import.reviewOnly","true","hydra.import.safeTranslation","false","hydra.import.originalFix",fragment);
            fixes.add(new RecoveredFix(name.replaceFirst("^(?:fix|quickfix|repair)_",""),new ActionDefinition(id,name,"Imported BESSER/Python repair preserved statically. Translate it to canonical HYDRA operations before enabling it.",false,List.of(),"",List.of(),ActionTransactionPolicy.BEST_EFFORT,ActionUndoPolicy.NOT_SUPPORTED,annotations))); recovered++;
        }
    }

    /** Descripción: Ejecuta la operación «linkedFixes» correspondiente a la responsabilidad de ExpressionState. Entradas: fixesByRule: dato de entrada necesario para la operación; keys: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<RecoveredFix> linkedFixes(Map<String,List<RecoveredFix>> fixesByRule,String...keys) {
        List<RecoveredFix> result=new ArrayList<>(); for(String key:keys) if(key!=null&&!key.isBlank()) result.addAll(fixesByRule.getOrDefault(key.toLowerCase(Locale.ROOT),List.of())); return result;
    }

    /** Descripción: Ejecuta la operación «action» correspondiente a la responsabilidad de ExpressionState. Entradas: path: ruta o ubicación utilizada por la operación; contextName: dato de entrada necesario para la operación; contextClassId: identificador que delimita el recurso objetivo; ruleName: dato de entrada necesario para la operación; title: dato de entrada necesario para la operación; body: dato de entrada necesario para la operación; metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ActionDefinition action(String path, String contextName, String contextClassId, String ruleName,
            String title, String body, MetaModel metamodel) {
        String id = "action:external-" + sha256(path + "|" + contextName + "|" + ruleName + "|" + title).substring(0,16);
        List<ActionOperation> operations = safeOperations(id, body, contextClassId, metamodel);
        boolean reviewOnly = operations.isEmpty();
        Map<String,String> annotations = new LinkedHashMap<>();
        annotations.put("hydra.import.format", "EVL");
        annotations.put("hydra.import.validationRuleName", ruleName);
        annotations.put("hydra.import.contextClassId", contextClassId);
        annotations.put("hydra.import.originalContext", contextName);
        annotations.put("hydra.import.originalFix", body);
        annotations.put("hydra.import.reviewOnly", Boolean.toString(reviewOnly));
        annotations.put("hydra.import.safeTranslation", Boolean.toString(!reviewOnly));
        return new ActionDefinition(id,title, reviewOnly
                ? "Imported repair body preserved for review; HYDRA did not invent an executable translation."
                : "Imported repair translated deterministically to declarative HYDRA operations. It is previewable, transactional and undoable.",
                !reviewOnly,List.of(),"",operations, reviewOnly ? ActionTransactionPolicy.BEST_EFFORT : ActionTransactionPolicy.ATOMIC,
                reviewOnly ? ActionUndoPolicy.NOT_SUPPORTED : ActionUndoPolicy.REQUIRED,annotations);
    }

    /** Descripción: Ejecuta la operación «safeOperations» correspondiente a la responsabilidad de ExpressionState. Entradas: actionId: identificador que delimita el recurso objetivo; body: dato de entrada necesario para la operación; contextClassId: identificador que delimita el recurso objetivo; metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<ActionOperation> safeOperations(String actionId, String body, String contextClassId, MetaModel metamodel) {
        Matcher matcher = SIMPLE_ASSIGN.matcher(body);
        if(!matcher.matches()) return List.of();
        MetaAttribute attribute = resolveAttribute(metamodel, contextClassId, matcher.group(1));
        if(attribute == null) return List.of();
        Object literal = literal(matcher.group(2));
        if(literal == UnsupportedLiteral.INSTANCE) return List.of();
        return List.of(new SetAttributeOperation("operation:" + sha256(actionId + "|" + attribute.id()).substring(0,16),
                new ActionSelector(ActionSelectorKind.SELF,"",""), attribute.id(), ActionValueSource.literal(literal)));
    }

    /** Descripción: Ejecuta la operación «placeholderExpression» correspondiente a la responsabilidad de ExpressionState. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionDefinition placeholderExpression(RecoveredRule rule) {
        return new ExpressionDefinition(rule.expressionId(), rule.name() + " · external condition",
                "Placeholder used only to keep the imported rule structurally editable. The authoritative condition remains in provenance.",
                rule.contextClassId(), ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN),
                new LiteralExpressionNode("node:" + sha256(rule.expressionId()).substring(0,16), ExpressionValueType.scalar(ExpressionValueKind.BOOLEAN), true),
                Map.of("hydra.import.reviewOnly","true","hydra.import.originalCondition",rule.condition(),"hydra.import.technology",rule.technology()));
    }

    /** Descripción: Ejecuta la operación «expressionState» correspondiente a la responsabilidad de ExpressionState. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ExpressionState expressionState(String metamodelId) {
        try { var found=expressions.find(metamodelId); return new ExpressionState(found.expressionModel(),found.etag()); }
        catch(ExpressionModelApplicationException exception) {
            if(exception.getReason()!=ExpressionModelApplicationException.Reason.NOT_FOUND) throw exception;
            return new ExpressionState(new ExpressionModel(ExpressionModel.CURRENT_SCHEMA_VERSION,metamodelId,metamodelId,List.of(),Map.of()),"");
        }
    }
    /** Descripción: Ejecuta la operación «actionState» correspondiente a la responsabilidad de ExpressionState. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ActionState actionState(String metamodelId) {
        try { var found=actions.find(metamodelId); return new ActionState(found.actionModel(),found.etag()); }
        catch(ActionModelApplicationException exception) {
            if(exception.getReason()!=ActionModelApplicationException.Reason.NOT_FOUND) throw exception;
            return new ActionState(new ActionModel(ActionModel.CURRENT_SCHEMA_VERSION,documentId(metamodelId, "actions"),metamodelId,metamodelId,List.of(),Map.of()),"");
        }
    }
    /** Descripción: Ejecuta la operación «validationState» correspondiente a la responsabilidad de ExpressionState. Entradas: metamodelId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private ValidationState validationState(String metamodelId) {
        try { var found=validations.find(metamodelId); return new ValidationState(found.validationModel(),found.etag()); }
        catch(ValidationModelApplicationException exception) {
            if(exception.getReason()!=ValidationModelApplicationException.Reason.NOT_FOUND) throw exception;
            return new ValidationState(new ValidationModel(ValidationModel.CURRENT_SCHEMA_VERSION,documentId(metamodelId, "validations"),metamodelId,metamodelId,"",List.of(),List.of(),List.of(),Map.of()),"");
        }
    }

    /** Descripción: Ejecuta la operación «source» correspondiente a la responsabilidad de ExpressionState. Entradas: entry: dato de entrada necesario para la operación; technology: dato de entrada necesario para la operación; now: dato de entrada necesario para la operación; status: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ImportedValidationSource source(ExternalProjectArchiveEntry entry, String technology, Instant now, ConversionStatus status) {
        return new ImportedValidationSource("source:"+sha256(entry.path()+"|"+entry.sha256()).substring(0,16),technology,entry.path(),entry.sha256(),status,now,
                Map.of("hydra.import.externalProject","true"));
    }
    /** Descripción: Evalúa la condición «isLangiumSource» sin modificar el estado externo. Entradas: lower: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isLangiumSource(String lower,String source) { String text=source.toLowerCase(Locale.ROOT); return lower.contains("langium") || text.contains("from 'langium") || text.contains("from \"langium") || text.contains("validationacceptor") || text.contains("validationchecks<"); }
    /** Descripción: Evalúa la condición «isBesserSource» sin modificar el estado externo. Entradas: lower: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isBesserSource(String lower,String source) { String text=source.toLowerCase(Locale.ROOT); return lower.contains("besser") || text.contains("from besser") || text.contains("import besser") || text.contains("besser."); }
    /** Descripción: Evalúa la condición «isXtextValidator» sin modificar el estado externo. Entradas: lower: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isXtextValidator(String lower, String source) { return lower.endsWith(".java") && (lower.contains("validator") || source.contains("@Check")); }
    /** Descripción: Evalúa la condición «isXtextQuickFix» sin modificar el estado externo. Entradas: lower: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isXtextQuickFix(String lower, String source) { return lower.endsWith(".java") && (lower.contains("quickfix") || source.contains("@Fix")); }
    /** Descripción: Obtiene la información correspondiente a «resolveClass» dentro de ExpressionState. Entradas: metamodel: dato de entrada necesario para la operación; external: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClass resolveClass(MetaModel metamodel, String external) {
        String target = normalizeType(external);
        return metamodel.classes().stream().filter(value -> value.name().equalsIgnoreCase(target) || value.id().equalsIgnoreCase(external)).findFirst().orElse(null);
    }
    /** Descripción: Obtiene la información correspondiente a «resolveAttribute» dentro de ExpressionState. Entradas: metamodel: dato de entrada necesario para la operación; classId: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaAttribute resolveAttribute(MetaModel metamodel, String classId, String name) {
        List<MetaAttribute> direct = metamodel.classes().stream().filter(value -> value.id().equals(classId)).flatMap(value -> value.attributes().stream())
                .filter(value -> value.name().equalsIgnoreCase(name)).toList();
        if(direct.size()==1) return direct.get(0);
        List<MetaAttribute> global = metamodel.classes().stream().flatMap(value -> value.attributes().stream()).filter(value -> value.name().equalsIgnoreCase(name)).toList();
        return global.size()==1 ? global.get(0) : null;
    }
    /** Descripción: Ejecuta la operación «normalizeType» correspondiente a la responsabilidad de ExpressionState. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalizeType(String value) { return value == null ? "" : value.replaceAll(".*[!:]", "").replaceAll("[^A-Za-z0-9_]", ""); }
    /** Descripción: Ejecuta la operación «literal» correspondiente a la responsabilidad de ExpressionState. Entradas: raw: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Object literal(String raw) {
        String value=raw.trim();
        if((value.startsWith("'")&&value.endsWith("'"))||(value.startsWith("\"")&&value.endsWith("\""))) return value.substring(1,value.length()-1).replace("''", "'");
        if("true".equalsIgnoreCase(value)||"false".equalsIgnoreCase(value)) return Boolean.valueOf(value);
        try { if(value.matches("[-+]?\\d+")) return Integer.valueOf(value); if(value.matches("[-+]?\\d+\\.\\d+")) return new java.math.BigDecimal(value); }
        catch(NumberFormatException ignored) { /* Non-literal numeric text remains external instead of being coerced. */ }
        return UnsupportedLiteral.INSTANCE;
    }
    /** Descripción: Ejecuta la operación «field» correspondiente a la responsabilidad de ExpressionState. Entradas: body: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String field(String body, String name) {
        Matcher matcher=Pattern.compile("(?is)\\b"+Pattern.quote(name)+"\\s*:\\s*(.*?)(?=\\r?\\n\\s*(?:(?:check|guard|message|title)\\s*:|do\\s*(?::|\\{)|fix\\b)|$)").matcher(body);
        return matcher.find()?matcher.group(1).trim().replaceFirst(";\\s*$",""):"";
    }
    /** Descripción: Ejecuta la operación «unquote» correspondiente a la responsabilidad de ExpressionState. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String unquote(String value) { String s=value.trim(); return s.length()>=2&&((s.startsWith("'")&&s.endsWith("'"))||(s.startsWith("\"")&&s.endsWith("\"")))?s.substring(1,s.length()-1):s; }
    /** Descripción: Ejecuta la operación «blocks» correspondiente a la responsabilidad de ExpressionState. Entradas: source: dato de entrada necesario para la operación; header: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<Block> blocks(String source, Pattern header) {
        List<Block> result=new ArrayList<>(); Matcher matcher=header.matcher(source); int search=0;
        while(matcher.find(search)) { int open=source.indexOf('{',matcher.start()); if(open<0)break; int close=matchingBrace(source,open); if(close<0)break;
            List<String> groups=new ArrayList<>(); for(int i=1;i<=matcher.groupCount();i++)groups.add(matcher.group(i));
            result.add(new Block(source.substring(matcher.start(),open+1),source.substring(open+1,close),source.substring(matcher.start(),close+1),groups)); search=close+1; matcher=header.matcher(source); }
        return result;
    }
    /** Descripción: Ejecuta la operación «matchingBrace» correspondiente a la responsabilidad de ExpressionState. Entradas: source: dato de entrada necesario para la operación; open: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int matchingBrace(String source,int open) { int depth=0; boolean single=false,dbl=false,escape=false; for(int i=open;i<source.length();i++){char c=source.charAt(i);if(escape){escape=false;continue;}if(c=='\\'){escape=true;continue;}if(!dbl&&c=='\'')single=!single;else if(!single&&c=='\"')dbl=!dbl;if(single||dbl)continue;if(c=='{')depth++;else if(c=='}'&&--depth==0)return i;}return -1; }
    /** Limita la inspección a formatos que pueden contener validaciones/quick fixes recuperables. */
    /** Descripción: Ejecuta la operación «potentialValidationSource» correspondiente a la responsabilidad de UnsupportedLiteral. Entradas: lower: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean potentialValidationSource(String lower) {
        return lower.endsWith(".evl") || lower.endsWith(".ocl") || lower.endsWith(".odesign")
                || lower.endsWith(".java") || lower.endsWith(".ts") || lower.endsWith(".tsx") || lower.endsWith(".py");
    }
    /** Descripción: Construye un identificador de documento derivado válido sin introducir separadores reservados a IDs de elementos. Entrada: base: identificador base; suffix: sufijo funcional del documento. Salida: devuelve un ID de documento estable y acotado. */
    /** Descripción: Ejecuta la operación «documentId» correspondiente a la responsabilidad de UnsupportedLiteral. Entradas: base: dato de entrada necesario para la operación; suffix: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String documentId(String base, String suffix) {
        String normalized = (base == null ? "" : base.trim()).replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        if(normalized.isBlank()) normalized = "imported";
        String result = normalized + "." + suffix;
        return result.length() <= 120 ? result : result.substring(0, 120);
    }

    /** Descripción: Ejecuta la operación «code» correspondiente a la responsabilidad de ExpressionState. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String code(String value) { String c=value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9._:-]+","_").replaceAll("^_+|_+$",""); return c.isBlank()?"IMPORTED_VALIDATION":c; }
    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de ExpressionState. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(String value) { try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((value==null?"":value).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);} }
    /** Descripción: Actualiza el estado gestionado por ExpressionState mediante la operación «merge». Entradas: first: dato de entrada necesario para la operación; second: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,String> merge(Map<String,String> first,Map<String,String> second){Map<String,String> result=new LinkedHashMap<>();if(first!=null)result.putAll(first);if(second!=null)result.putAll(second);return result;}

    private enum UnsupportedLiteral { INSTANCE }
    private record Block(String header,String body,String full,List<String> groups){String group(int index){return index<=0||index>groups.size()?null:groups.get(index-1);}}
    private record RecoveredRule(String ruleId,String expressionId,String name,String code,String contextClassId,String technology,String sourcePath,String condition,String guard,String message,ValidationSeverity severity,AuthoritativeRepresentation authoritativeRepresentation,String originalFragment){}
    private record RecoveredFix(String ruleName,ActionDefinition action){}
    private record ExpressionState(ExpressionModel model,String etag){}
    private record ActionState(ActionModel model,String etag){}
    private record ValidationState(ValidationModel model,String etag){}
}
