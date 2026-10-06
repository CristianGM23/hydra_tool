package com.example.editor.metamodel.importing.support;

import static com.example.editor.metamodel.importing.support.TextualGrammarImportParser.*;
import static com.example.editor.metamodel.importing.support.TextualGrammarSyntaxEngine.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.importing.canonicalization.EcoreImportMetadata;

/** Mutable builders used while projecting textual grammar declarations to the neutral metamodel. */
final class TextualGrammarModelEngine {
    /** Descripción: Inicializa una instancia de TextualGrammarModelEngine con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private TextualGrammarModelEngine() {
    }

    static final class NameAllocator {
        final Set<String> used = new LinkedHashSet<>();
        final List<TransformationWarning> warnings;

        /** Descripción: Inicializa una instancia de NameAllocator con sus dependencias y datos de entrada. Entradas: warnings: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ NameAllocator(List<TransformationWarning> warnings) {
            this.warnings = warnings;
        }

        /** Descripción: Ejecuta la operación «allocate» correspondiente a la responsabilidad de EnumRegistry. Entradas: requested: solicitud con los datos necesarios para ejecutar la operación; suffixWord: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; category: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ String allocate(String requested, String suffixWord, String path, String category) {
            String base = ImportParsingSupport.pascal(requested, suffixWord);
            String candidate = base;
            int suffix = 2;

            if(used.contains(candidate.toLowerCase(Locale.ROOT))) {
                candidate = base.endsWith(suffixWord) ? base + suffix : base + suffixWord;

                while(used.contains(candidate.toLowerCase(Locale.ROOT))) {
                    suffix += 1;
                    candidate = base + suffixWord + suffix;
                }

                warnings.add(new TransformationWarning("IMPORT_CLASSIFIER_NAME_CONFLICT_AUTO_FIXED", path, "Classifier name " + base + " conflicts with another imported classifier and was renamed to " + candidate + " for the " + category));
            }

            used.add(candidate.toLowerCase(Locale.ROOT));
            return candidate;
        }
    }

    static final class EnumRegistry {
        final NameAllocator classifierNames;
        final List<TransformationWarning> warnings;
        final Map<String, MetaEnum> byRule = new LinkedHashMap<>();

        /** Descripción: Inicializa una instancia de EnumRegistry con sus dependencias y datos de entrada. Entradas: classifierNames: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ EnumRegistry(NameAllocator classifierNames, List<TransformationWarning> warnings) {
            this.classifierNames = classifierNames;
            this.warnings = warnings;
        }

        /** Descripción: Ejecuta la operación «register» correspondiente a la responsabilidad de EnumRegistry. Entradas: rule: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void register(GrammarRule rule) {
            String desired = isQualifiedClassifierReturn(rule.returnsType()) ? simpleTypeName(rule.returnsType()) : rule.name();
            String name = classifierNames.allocate(desired, "Enum", path(rule), "enumeration");
            byRule.put(rule.name(), buildEnum(rule, name, warnings));
        }

        /** Descripción: Ejecuta la operación «inline» correspondiente a la responsabilidad de EnumRegistry. Entradas: className: dato de entrada necesario para la operación; featureName: dato de entrada necesario para la operación; values: colección de elementos que debe procesarse; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ MetaEnum inline(String className, String featureName, List<String> values, String path) {
            String ruleKey = "@inline:" + className + ":" + featureName;
            MetaEnum existing = byRule.get(ruleKey);

            if(existing != null) {
                return existing;
            }

            String desired = ImportParsingSupport.pascal(className + " " + featureName, "InlineEnum");
            String name = classifierNames.allocate(desired, "Enum", path, "inline enumeration");
            GrammarRule synthetic = new GrammarRule(ruleKey, "", String.join(" | ", values.stream().map(value -> "'" + value.replace("'", "\\'") + "'").toList()), false, RuleKind.ENUM, 0, 0);
            MetaEnum value = buildEnum(synthetic, name, warnings);
            byRule.put(ruleKey, value);
            warnings.add(new TransformationWarning("IMPORT_INLINE_ENUM_CREATED", path, "Inline literal alternatives were converted into editable enumeration " + name));
            return value;
        }

        /** Descripción: Evalúa la condición «containsRule» sin modificar el estado externo. Entradas: ruleName: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ boolean containsRule(String ruleName) {
            return byRule.containsKey(ruleName);
        }

        /** Descripción: Obtiene la información correspondiente a «get» dentro de EnumRegistry. Entradas: ruleName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ MetaEnum get(String ruleName) {
            return byRule.get(ruleName);
        }

        /** Descripción: Ejecuta la operación «values» correspondiente a la responsabilidad de EnumRegistry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ List<MetaEnum> values() {
            return List.copyOf(byRule.values());
        }

        /** Descripción: Crea o prepara el resultado de «buildEnum» dentro de EnumRegistry. Entradas: rule: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static MetaEnum buildEnum(GrammarRule rule, String name, List<TransformationWarning> warnings) {
            String id = "enum:" + ImportParsingSupport.safeId(name, "enumeration");
            List<EnumLiteralSource> sourceLiterals = parseEnumLiterals(rule.body());

            if(sourceLiterals.isEmpty()) {
                sourceLiterals = List.of(new EnumLiteralSource("UNSPECIFIED", "UNSPECIFIED"));
                warnings.add(new TransformationWarning("IMPORT_ENUM_EMPTY_AUTO_FIXED", path(rule), "Enumeration " + name + " did not contain a readable literal and received the fallback literal UNSPECIFIED"));
            }

            List<MetaEnumLiteral> literals = new ArrayList<>();
            Set<String> names = new LinkedHashSet<>();
            Map<String, String> serializedOwners = new LinkedHashMap<>();

            for(int index = 0; index < sourceLiterals.size(); index += 1) {
                EnumLiteralSource source = sourceLiterals.get(index);
                String originalName = source.name();
                String literalName = sanitizeLiteralName(originalName, index);

                if(!literalName.equals(originalName)) {
                    warnings.add(new TransformationWarning("IMPORT_ENUM_LITERAL_INVALID_AUTO_FIXED", path(rule) + ".literals[" + index + "]", "Enum literal name " + originalName + " is invalid for the neutral metamodel and was renamed to " + literalName));
                }

                String baseName = literalName;
                int suffix = 2;

                while(!names.add(literalName.toLowerCase(Locale.ROOT))) {
                    literalName = baseName + "_" + suffix;
                    suffix += 1;
                }

                if(!literalName.equals(baseName)) {
                    warnings.add(new TransformationWarning("IMPORT_ENUM_LITERAL_NAME_DUPLICATE_AUTO_FIXED", path(rule) + ".literals[" + index + "]", "Repeated enum literal name " + baseName + " was renamed to " + literalName));
                }

                String serialized = source.serializedValue();

                if(serialized == null || serialized.isBlank()) {
                    serialized = literalName;
                    warnings.add(new TransformationWarning("IMPORT_ENUM_LITERAL_VALUE_INVALID_AUTO_FIXED", path(rule) + ".literals[" + index + "]", "Enum literal " + literalName + " had an empty serialized value and now uses its literal name"));
                }

                String previous = serializedOwners.putIfAbsent(serialized, literalName);

                if(previous != null) {
                    warnings.add(new TransformationWarning("IMPORT_ENUM_LITERAL_VALUE_DUPLICATE", path(rule) + ".literals[" + index + "]", "Serialized enum value " + serialized + " is repeated by literals " + previous + " and " + literalName + "; both literals were preserved but the source grammar may be ambiguous"));
                }

                Map<String, String> annotations = new LinkedHashMap<>();
                annotations.put("serializedValue", serialized);

                if(!originalName.equals(literalName)) {
                    annotations.put("sourceName", originalName);
                }

                literals.add(new MetaEnumLiteral(ImportParsingSupport.literalId(id, literalName) + ":" + index, literalName, index, annotations));
            }

            return new MetaEnum(id, name, literals, Map.of("sourceRule", rule.name(), "sourceLine", Integer.toString(rule.line())));
        }

        /** Descripción: Ejecuta la operación «parseEnumLiterals» correspondiente a la responsabilidad de EnumRegistry. Entradas: body: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static List<EnumLiteralSource> parseEnumLiterals(String body) {
            List<EnumLiteralSource> result = new ArrayList<>();
            Matcher explicit = EXPLICIT_ENUM_LITERAL.matcher(body);

            while(explicit.find()) {
                result.add(new EnumLiteralSource(explicit.group(1).trim(), unquote(explicit.group(2))));
            }

            if(!result.isEmpty()) {
                return result;
            }

            for(String value : quotedValues(body)) {
                result.add(new EnumLiteralSource(sanitizeLiteralName(value, result.size()), value));
            }

            if(!result.isEmpty()) {
                return result;
            }

            for(String part : body.split("[,|\\s]+")) {
                if(!part.isBlank() && !"=".equals(part)) {
                    result.add(new EnumLiteralSource(part.trim(), part.trim()));
                }
            }

            return result;
        }

        /** Descripción: Ejecuta la operación «sanitizeLiteralName» correspondiente a la responsabilidad de EnumRegistry. Entradas: value: valor de entrada que debe procesarse; index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static String sanitizeLiteralName(String value, int index) {
            String source = value == null ? "" : value.trim();
            String normalized = source.replaceAll("[^A-Za-z0-9_]", "_").replaceAll("_+", "_").replaceAll("^_+|_+$", "");

            if(normalized.isBlank()) {
                normalized = "LITERAL_" + (index + 1);
            }

            if(!Character.isJavaIdentifierStart(normalized.charAt(0))) {
                normalized = "_" + normalized;
            }

            return normalized;
        }
    }

    record EnumLiteralSource(String name, String serializedValue) {
    }

    static final class ClassBuilder {
        final String ruleName;
        final String id;
        final String name;
        final MetaClassKind kind;
        final String body;
        final int line;
        final String sourceFormat;
        final Map<String, String> annotations = new LinkedHashMap<>();
        final List<Assignment> assignments = new ArrayList<>();
        final List<MetaAttribute> attributes = new ArrayList<>();
        final Set<String> identifierFeatures = new LinkedHashSet<>();
        final Set<String> usedFeatureNames = new LinkedHashSet<>();
        final Set<String> usedRelationIds = new LinkedHashSet<>();

        /** Descripción: Inicializa una instancia de ClassBuilder con sus dependencias y datos de entrada. Entradas: ruleName: dato de entrada necesario para la operación; id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; kind: dato de entrada necesario para la operación; body: dato de entrada necesario para la operación; line: dato de entrada necesario para la operación; entry: dato de entrada necesario para la operación; sourceFormat: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ ClassBuilder(String ruleName, String id, String name, MetaClassKind kind, String body, int line, boolean entry, String sourceFormat) {
            this.ruleName = ruleName;
            this.id = id;
            this.name = name;
            this.kind = kind;
            this.body = body == null ? "" : body;
            this.line = line;
            this.sourceFormat = sourceFormat;
            annotations.put("sourceRule", ruleName);
            annotations.put("sourceLine", Integer.toString(line));

            if(entry) {
                annotations.put("entryRule", "true");
            }
        }

        /** Descripción: Obtiene la información correspondiente a «readFeatureSpecs» dentro de EnumRegistry. Entradas: enums: dato de entrada necesario para la operación; dataTypes: dato de entrada necesario para la operación; classes: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void readFeatureSpecs(EnumRegistry enums, Map<String, MetaDataType> dataTypes, Map<String, ClassBuilder> classes, List<TransformationWarning> warnings) {
            assignments.addAll(readAssignments(body));

            for(Assignment assignment : assignments) {
                String expression = assignment.expression();
                String simple = simpleExpressionName(expression);

                if("ID".equalsIgnoreCase(simple) || "STRING".equalsIgnoreCase(simple)) {
                    identifierFeatures.add(assignment.featureName());
                }
            }
        }

        /** Descripción: Ejecuta la operación «materializeFeatures» correspondiente a la responsabilidad de EnumRegistry. Entradas: enums: dato de entrada necesario para la operación; dataTypes: dato de entrada necesario para la operación; classes: dato de entrada necesario para la operación; relations: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación; forwardingRules: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void materializeFeatures(EnumRegistry enums, Map<String, MetaDataType> dataTypes, Map<String, ClassBuilder> classes, List<MetaRelation> relations, List<TransformationWarning> warnings, Map<String, String> forwardingRules) {
            for(Assignment assignment : assignments) {
                String featurePath = "$.rules[" + ruleName + "].features[" + assignment.featureName() + "]@line" + line;
                String featureName = uniqueFeatureName(assignment.featureName(), usedFeatureNames, featurePath, warnings);
                Multiplicity featureMultiplicity = multiplicity(assignment);
                String expression = assignment.expression();

                if(expression.startsWith("[")) {
                    CrossReference reference = parseCrossReference(expression);
                    String resolvedTargetRule = resolveForwardingRule(reference.targetRule(), forwardingRules);
                    ClassBuilder target = classes.get(resolvedTargetRule);

                    if(target == null) {
                        warnings.add(new TransformationWarning("IMPORT_UNRESOLVED_CROSS_REFERENCE_TARGET", featurePath, "Cross-reference " + ruleName + "." + featureName + " targets unknown rule " + reference.targetRule()));
                        continue;
                    }

                    String scopeKey = inferScopeKey(target);
                    Map<String, String> relationAnnotations = new LinkedHashMap<>();
                    relationAnnotations.put("sourceRule", ruleName);
                    relationAnnotations.put("referenceSyntax", reference.syntax());
                    relationAnnotations.put("referenceTerminal", reference.terminalRule().isBlank() ? "ID" : reference.terminalRule());
                    relationAnnotations.put("scopeMode", "default-or-external");

                    if(!scopeKey.isBlank()) {
                        relationAnnotations.put("scopeKeyFeature", scopeKey);
                    }

                    String relationId = uniqueId(ImportParsingSupport.relationId(id, featureName), usedRelationIds);
                    relations.add(new MetaRelation(relationId, featureName, id, target.id, MetaRelationKind.REFERENCE, null, featureMultiplicity, "", true, true, EcoreImportMetadata.relation(relationAnnotations, true, true)));
                    warnings.add(new TransformationWarning("IMPORT_INFO_CROSS_REFERENCE_DETECTED", featurePath, "Cross-reference " + ruleName + "." + featureName + " -> " + reference.targetRule() + " was preserved using terminal " + (reference.terminalRule().isBlank() ? "ID" : reference.terminalRule())));

                    if(scopeKey.isBlank()) {
                        warnings.add(new TransformationWarning("IMPORT_SCOPE_KEY_NOT_FOUND", featurePath, "No ID or STRING identifying feature was found in target rule " + resolvedTargetRule + "; the target type was preserved but automatic scope resolution may require manual configuration"));
                    } else {
                        warnings.add(new TransformationWarning("IMPORT_SCOPE_EXTERNAL_CONFIGURATION", featurePath, "Scope for " + ruleName + "." + featureName + " is not fully declared inside a " + sourceFormat + " grammar; target " + reference.targetRule() + " and candidate key " + scopeKey + " were preserved, but custom scope providers must be reviewed separately"));
                    }

                    if(!reference.terminalRule().isBlank() && !isPrimitiveExpression(reference.terminalRule())) {
                        warnings.add(new TransformationWarning("IMPORT_CROSS_REFERENCE_TERMINAL_UNKNOWN", featurePath, "Cross-reference terminal " + reference.terminalRule() + " is custom or unresolved; the relation target was preserved"));
                    }

                    continue;
                }

                String simple = resolveForwardingRule(simpleExpressionName(expression), forwardingRules);
                ClassBuilder target = classes.get(simple);

                if(target != null) {
                    String relationId = uniqueId(ImportParsingSupport.relationId(id, featureName), usedRelationIds);
                    relations.add(new MetaRelation(relationId, featureName, id, target.id, MetaRelationKind.CONTAINMENT, null, featureMultiplicity, "", true, true, EcoreImportMetadata.relation(Map.of("sourceRule", ruleName), true, false)));
                    continue;
                }

                MetaTypeReference type;

                if(enums.containsRule(simple)) {
                    type = MetaTypeReference.enumType(enums.get(simple).id());
                } else if(dataTypes.containsKey(simple)) {
                    type = MetaTypeReference.dataType(dataTypes.get(simple).id());
                } else if("?=".equals(assignment.operator())) {
                    type = MetaTypeReference.primitive(MetaPrimitiveType.BOOLEAN);
                } else if(expression.startsWith("(")) {
                    List<String> values = quotedValues(expression);

                    if(values.size() > 1) {
                        type = MetaTypeReference.enumType(enums.inline(name, featureName, values, featurePath).id());
                    } else if(values.size() == 1) {
                        type = MetaTypeReference.primitive(MetaPrimitiveType.STRING);
                    } else {
                        List<String> alternatives = alternativeNames(expression.substring(1, Math.max(1, expression.length() - 1)));

                        if(alternatives.size() > 1) {
                            type = MetaTypeReference.enumType(enums.inline(name, featureName, alternatives, featurePath).id());
                        } else {
                            type = MetaTypeReference.primitive(MetaPrimitiveType.OBJECT);
                            warnings.add(new TransformationWarning("IMPORT_GROUPED_ATTRIBUTE_TYPE_AMBIGUOUS", featurePath, "Grouped assignment type " + expression + " could not be represented precisely and was imported as Object"));
                        }
                    }
                } else if(expression.startsWith("'") || expression.startsWith("\"")) {
                    type = MetaTypeReference.primitive(MetaPrimitiveType.STRING);
                } else if(isPrimitiveExpression(simple)) {
                    type = primitiveType(simple);
                } else if(!simple.isBlank()) {
                    type = ImportParsingSupport.typeReference(simple);
                } else {
                    type = MetaTypeReference.primitive(MetaPrimitiveType.OBJECT);
                }

                attributes.add(new MetaAttribute(ImportParsingSupport.attributeId(id, featureName), featureName, type, featureMultiplicity, true, true, "", Map.of("sourceRule", ruleName)));
            }
        }

        /** Descripción: Crea o prepara el resultado de «build» dentro de EnumRegistry. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ MetaClass build() {
            return new MetaClass(id, name, kind, attributes, List.of(), annotations);
        }
    }
}
