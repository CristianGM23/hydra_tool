package com.example.editor.metamodel.importing.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.example.editor.metamodel.importing.canonicalization.EcoreImportMetadata;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.core.TransformationWarning;

/**
 * Reconstruye metamodelos desde gramáticas Xtext, Langium y ANTLR.
 */
public final class GeneratedGrammarImportSupport {

    private static final Pattern QUOTED_LITERAL = Pattern.compile("'([^']+)'");
    private static final Pattern ANTLR_RULE = Pattern.compile("(?ms)^([a-z][A-Za-z0-9_]*)\\s*:\\s*(.*?)\\s*;");
    private static final Pattern TRANSPORT_RULE = Pattern.compile("(?ms)^\\s*//\\s*HYDRA_TRANSPORT_RULE\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\R\\s*(?:entry\\s+)?\\1\\s*:\\s*.*?;\\s*");
    private static final Pattern ANTLR_FEATURE = Pattern.compile("\\('([^']+)'\\s+([A-Za-z_][A-Za-z0-9_]*|\\([^)]*\\))\\s*(?:/\\*\\s*HYDRA_REFERENCE\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\*/\\s*)?\\)([?*+]?)");

    /** Descripción: Inicializa una instancia de GeneratedGrammarImportSupport con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private GeneratedGrammarImportSupport() {
    }

    /** Descripción: Ejecuta la operación «parseTextual» correspondiente a la responsabilidad de AntlrClassBuilder. Entradas: content: valor de entrada que debe procesarse; grammarName: dato de entrada necesario para la operación; namespaceUri: dato de entrada necesario para la operación; sourceFormat: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static ParsedGrammar parseTextual(String content, String grammarName, String namespaceUri, String sourceFormat) {
        return TextualGrammarImportParser.parse(removeTransportRules(content), grammarName, namespaceUri, sourceFormat);
    }


    /** Descripción: Elimina exclusivamente las reglas auxiliares marcadas por los exportadores HYDRA antes de inferir el metamodelo, evitando que el envoltorio de transporte se convierta en clases falsas. Entradas: content: fuente textual. Salida: devuelve la fuente semántica sin reglas auxiliares marcadas. */ private static String removeTransportRules(String content) {
        String source=content==null?"":content;
        String previous;
        do { previous=source; source=TRANSPORT_RULE.matcher(source).replaceAll(""); } while(!source.equals(previous));
        return source;
    }

    /** Descripción: Ejecuta la operación «parseAntlr» correspondiente a la responsabilidad de AntlrClassBuilder. Entradas: content: valor de entrada que debe procesarse; grammarName: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static ParsedGrammar parseAntlr(String content, String grammarName) {
        Map<String, AntlrRule> rules = new LinkedHashMap<>();
        Matcher matcher = ANTLR_RULE.matcher(content);

        while(matcher.find()) {
            rules.put(matcher.group(1), new AntlrRule(matcher.group(1), matcher.group(2)));
        }

        Map<String, MetaEnum> enums = new LinkedHashMap<>();
        Map<String, AntlrClassBuilder> classBuilders = new LinkedHashMap<>();
        List<TransformationWarning> warnings = new ArrayList<>();

        for(AntlrRule rule : rules.values()) {
            if(isAntlrClassRule(rule)) {
                String keyword = firstQuoted(rule.body);
                String name = ImportParsingSupport.pascal(keyword, ImportParsingSupport.pascal(rule.name, "Class"));
                classBuilders.put(rule.name, new AntlrClassBuilder("class:" + ImportParsingSupport.safeId(name, "class"), name, rule.body));
            } else if(isAntlrEnumRule(rule)) {
                String id = "enum:" + ImportParsingSupport.safeId(rule.name, "enumeration");
                List<String> values = quotedValues(rule.body);
                List<MetaEnumLiteral> literals = new ArrayList<>();

                for(int index = 0; index < values.size(); index += 1) {
                    String value = values.get(index);
                    literals.add(new MetaEnumLiteral(ImportParsingSupport.literalId(id, value), value, index, Map.of()));
                }

                enums.put(rule.name, new MetaEnum(id, ImportParsingSupport.pascal(rule.name, "Enumeration"), literals, Map.of("sourceRule", rule.name)));
            }
        }

        List<MetaRelation> relations = new ArrayList<>();

        for(Map.Entry<String, AntlrClassBuilder> entry : classBuilders.entrySet()) {
            entry.getValue().readFeatures(entry.getKey(), classBuilders, enums, relations, warnings);
        }

        String id = ImportParsingSupport.safeId(grammarName, "imported-antlr");
        MetaModel metamodel = new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, id, ImportParsingSupport.pascal(grammarName, "ImportedAntlr"), classBuilders.values().stream().map(AntlrClassBuilder::build).toList(), List.copyOf(enums.values()), List.of(), relations, EcoreImportMetadata.model(Map.of("sourceFormat", "antlr"), id + ".ecore", "urn:imported:" + id, id.replace('-', '_')));
        warnings.add(new TransformationWarning("IMPORT_LOSSY_ANTLR_METADATA", "$.classes", "ANTLR does not retain interfaces, inheritance, Ecore feature flags or stable identifiers in the generated grammar, so deterministic identifiers and safe defaults were created"));
        return new ParsedGrammar(metamodel, warnings);
    }

    /** Descripción: Evalúa la condición «isAntlrClassRule» sin modificar el estado externo. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean isAntlrClassRule(AntlrRule rule) {
        return rule.body.contains("'{'") && rule.body.contains("'}'");
    }

    /** Descripción: Evalúa la condición «isAntlrEnumRule» sin modificar el estado externo. Entradas: rule: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean isAntlrEnumRule(AntlrRule rule) {
        return !isAntlrClassRule(rule) && !"model".equals(rule.name) && !"element".equals(rule.name) && !quotedValues(rule.body).isEmpty() && rule.body.replaceAll("'[^']+'", "").replace("|", "").replaceAll("\\s+", "").isBlank();
    }

    /** Descripción: Ejecuta la operación «firstQuoted» correspondiente a la responsabilidad de AntlrClassBuilder. Entradas: body: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String firstQuoted(String body) {
        Matcher matcher = QUOTED_LITERAL.matcher(body);
        return matcher.find() ? matcher.group(1) : "GeneratedClass";
    }

    /** Descripción: Ejecuta la operación «quotedValues» correspondiente a la responsabilidad de AntlrClassBuilder. Entradas: body: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> quotedValues(String body) {
        List<String> result = new ArrayList<>();
        Matcher matcher = QUOTED_LITERAL.matcher(body);

        while(matcher.find()) {
            String value = matcher.group(1);

            if(!"{".equals(value) && !"}".equals(value)) {
                result.add(value);
            }
        }

        return result;
    }

    public record ParsedGrammar(MetaModel metamodel, List<TransformationWarning> warnings) {

        public ParsedGrammar {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    private record AntlrRule(String name, String body) {
    }

    private static final class AntlrClassBuilder {
        private final String id;
        private final String name;
        private final String body;
        private final List<MetaAttribute> attributes = new ArrayList<>();

        /** Descripción: Inicializa una instancia de AntlrClassBuilder con sus dependencias y datos de entrada. Entradas: id: identificador que delimita el recurso objetivo; name: dato de entrada necesario para la operación; body: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private AntlrClassBuilder(String id, String name, String body) {
            this.id = id;
            this.name = name;
            this.body = body;
        }

        /** Descripción: Detecta si una regla de clase ANTLR declara un identificador de nombre inmediatamente después de su palabra clave. Entradas: body: cuerpo de la regla. Salida: devuelve true únicamente cuando el ID representa el nombre canónico del elemento. */ private static boolean hasNameIdentifier(String body) {
            return body != null && body.matches("(?s)^\\s*'[^']+'\\s+ID\\b.*");
        }

        /** Descripción: Obtiene la información correspondiente a «readFeatures» dentro de AntlrClassBuilder. Entradas: ruleName: dato de entrada necesario para la operación; classes: dato de entrada necesario para la operación; enums: dato de entrada necesario para la operación; relations: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void readFeatures(String ruleName, Map<String, AntlrClassBuilder> classes, Map<String, MetaEnum> enums, List<MetaRelation> relations, List<TransformationWarning> warnings) {
            if(hasNameIdentifier(body)) attributes.add(new MetaAttribute(ImportParsingSupport.attributeId(id, "name"), "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, "", Map.of("sourceRule", ruleName)));
            Matcher matcher = ANTLR_FEATURE.matcher(body);

            while(matcher.find()) {
                String featureName = ImportParsingSupport.camel(matcher.group(1), "value");
                String expression = matcher.group(2);
                String referenceTargetRule = matcher.group(3);
                Multiplicity multiplicity = antlrMultiplicity(matcher.group(4));

                if(classes.containsKey(expression)) {
                    AntlrClassBuilder target = classes.get(expression);
                    relations.add(new MetaRelation(ImportParsingSupport.relationId(id, featureName), featureName, id, target.id, MetaRelationKind.CONTAINMENT, null, multiplicity, "", true, true, EcoreImportMetadata.relation(Map.of("sourceRule", ruleName), true, false)));
                } else if("ID".equals(expression) && referenceTargetRule != null && classes.containsKey(referenceTargetRule)) {
                    AntlrClassBuilder target = classes.get(referenceTargetRule);
                    relations.add(new MetaRelation(ImportParsingSupport.relationId(id, featureName), featureName, id, target.id, MetaRelationKind.REFERENCE, null, multiplicity, "", true, true, EcoreImportMetadata.relation(Map.of("sourceRule", ruleName, "hydraReferenceTargetRule", referenceTargetRule), false, true)));
                } else if("ID".equals(expression)) {
                    warnings.add(new TransformationWarning("IMPORT_LOSSY_ANTLR_REFERENCE_TARGET", "$.rules[" + ruleName + "]." + featureName, "ANTLR stores non-containment references as ID tokens; no HYDRA_REFERENCE metadata was available, so the target type could not be recovered"));
                    attributes.add(new MetaAttribute(ImportParsingSupport.attributeId(id, featureName), featureName, MetaTypeReference.primitive(MetaPrimitiveType.STRING), multiplicity, true, true, "", Map.of("sourceRule", ruleName, "originalKind", "reference-id")));
                } else if(enums.containsKey(expression)) {
                    attributes.add(new MetaAttribute(ImportParsingSupport.attributeId(id, featureName), featureName, MetaTypeReference.enumType(enums.get(expression).id()), multiplicity, true, true, "", Map.of("sourceRule", ruleName)));
                } else {
                    MetaTypeReference type = expression.startsWith("(") ? MetaTypeReference.primitive(MetaPrimitiveType.BOOLEAN) : ImportParsingSupport.typeReference(expression);
                    attributes.add(new MetaAttribute(ImportParsingSupport.attributeId(id, featureName), featureName, type, multiplicity, true, true, "", Map.of("sourceRule", ruleName)));
                }
            }
        }

        /** Descripción: Ejecuta la operación «antlrMultiplicity» correspondiente a la responsabilidad de AntlrClassBuilder. Entradas: quantifier: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Multiplicity antlrMultiplicity(String quantifier) {
            return switch(quantifier) {
                case "?" -> Multiplicity.optional();
                case "*" -> Multiplicity.many();
                case "+" -> new Multiplicity(1, null);
                default -> Multiplicity.one();
            };
        }

        /** Descripción: Crea o prepara el resultado de «build» dentro de AntlrClassBuilder. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClass build() {
            return new MetaClass(id, name, MetaClassKind.CLASS, attributes, List.of(), Map.of());
        }
    }
}
