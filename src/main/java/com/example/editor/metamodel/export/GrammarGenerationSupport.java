package com.example.editor.metamodel.export;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;

/**
 * Reúne reglas comunes para las gramáticas neutrales.
 */
public final class GrammarGenerationSupport {

    /** Descripción: Inicializa una instancia de GrammarGenerationSupport con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private GrammarGenerationSupport() {
    }

    /** Descripción: Ejecuta la operación «toPascalIdentifier» correspondiente a la responsabilidad de GrammarGenerationSupport. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String toPascalIdentifier(String value, String fallback) {
        List<String> words = splitWords(value);
        StringBuilder result = new StringBuilder();

        for(String word : words) {
            if(!word.isBlank()) {
                result.append(Character.toUpperCase(word.charAt(0)));

                if(word.length() > 1) {
                    result.append(word.substring(1));
                }
            }
        }

        if(result.isEmpty()) {
            result.append(fallback);
        }

        if(!Character.isJavaIdentifierStart(result.charAt(0))) {
            result.insert(0, 'M');
        }

        return removeInvalidIdentifierCharacters(result.toString());
    }

    /** Descripción: Ejecuta la operación «toCamelIdentifier» correspondiente a la responsabilidad de GrammarGenerationSupport. Entradas: value: valor de entrada que debe procesarse; fallback: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static String toCamelIdentifier(String value, String fallback) {
        String pascal = toPascalIdentifier(value, fallback);
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    /** Descripción: Ejecuta la operación «toKeyword» correspondiente a la responsabilidad de GrammarGenerationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static String toKeyword(String value) {
        return toCamelIdentifier(value, "element").toLowerCase(Locale.ROOT);
    }

    /** Descripción: Ejecuta la operación «quotedKeyword» correspondiente a la responsabilidad de GrammarGenerationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public static String quotedKeyword(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /** Descripción: Obtiene la información correspondiente a «findRootAlternatives» dentro de GrammarGenerationSupport. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static List<MetaClass> findRootAlternatives(NeutralMetamodelExportContext context) {
        Set<String> inheritedTargets = new HashSet<>();

        for(MetaRelation relation : context.neutralMetamodel().relations()) {
            if(relation.kind() == MetaRelationKind.INHERITANCE || relation.kind() == MetaRelationKind.IMPLEMENTATION) {
                inheritedTargets.add(relation.targetTypeId());
            }
        }

        List<MetaClass> roots = context.neutralMetamodel().classes().stream().filter(item -> item.kind() != MetaClassKind.INTERFACE).filter(item -> !inheritedTargets.contains(item.id())).toList();

        if(!roots.isEmpty()) {
            return roots;
        }

        return context.neutralMetamodel().classes().stream().filter(item -> item.kind() == MetaClassKind.CLASS).toList();
    }

    /** Descripción: Obtiene la información correspondiente a «findConcreteAssignableClasses» dentro de GrammarGenerationSupport. Entradas: context: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static List<MetaClass> findConcreteAssignableClasses(NeutralMetamodelExportContext context, MetaClass target) {
        List<MetaClass> result = new ArrayList<>();

        for(MetaClass candidate : context.neutralMetamodel().classes()) {
            if(candidate.kind() == MetaClassKind.CLASS && isAssignableTo(context, candidate.id(), target.id(), new HashSet<>())) {
                result.add(candidate);
            }
        }

        return result;
    }

    /** Descripción: Evalúa la condición «isAssignableTo» sin modificar el estado externo. Entradas: context: dato de entrada necesario para la operación; candidateId: identificador que delimita el recurso objetivo; targetId: identificador que delimita el recurso objetivo; visited: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private static boolean isAssignableTo(NeutralMetamodelExportContext context, String candidateId, String targetId, Set<String> visited) {
        if(!visited.add(candidateId)) {
            return false;
        }

        for(MetaRelation relation : MetamodelExportSupport.superTypeRelationsFrom(context, candidateId)) {
            if(relation.targetTypeId().equals(targetId) || isAssignableTo(context, relation.targetTypeId(), targetId, visited)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Descripción: Localiza la clase declarada explícitamente como raíz semántica del metamodelo sin inferir ni inventar una alternativa.
     * Entradas: context: contexto neutral que contiene el metamodelo y sus anotaciones canónicas.
     * Salida: devuelve la clase raíz declarada o {@code null} cuando el modelo no define una raíz semántica explícita.
     */
    public static MetaClass findSemanticRoot(NeutralMetamodelExportContext context) {
        for(MetaClass item : context.neutralMetamodel().classes()) {
            if("true".equalsIgnoreCase(item.annotations().get("root")) || "true".equalsIgnoreCase(item.annotations().get("entryRule"))) return item;
        }
        String rootId = context.neutralMetamodel().annotations().getOrDefault("rootClassId", "");
        if(!rootId.isBlank()) for(MetaClass item : context.neutralMetamodel().classes()) if(rootId.equals(item.id())) return item;
        return null;
    }

    /** Nombres auxiliares reservados por los exportadores para envolver las reglas del dominio sin colisionar con clases reales. */
    public record AuxiliaryRuleNames(String rootRule, String elementRule) { }

    /** Descripción: Calcula nombres deterministas para las reglas de transporte de una gramática y evita colisiones con clases, enumeraciones y tipos de datos del dominio. Entradas: context: metamodelo neutral que se exporta; camelCase: true para ANTLR y false para Xtext/Langium. Salida: devuelve dos nombres auxiliares únicos sin renombrar ninguna regla semántica del usuario. */ public static AuxiliaryRuleNames auxiliaryRuleNames(NeutralMetamodelExportContext context, boolean camelCase) {
        Set<String> occupied = new HashSet<>();
        context.neutralMetamodel().classes().forEach(item -> occupied.add(camelCase ? toCamelIdentifier(item.name(), "element") : toPascalIdentifier(item.name(), "Element")));
        context.neutralMetamodel().enums().forEach(item -> occupied.add(camelCase ? toCamelIdentifier(item.name(), "enumeration") : toPascalIdentifier(item.name(), "Enumeration")));
        context.neutralMetamodel().dataTypes().forEach(item -> occupied.add(camelCase ? toCamelIdentifier(item.name(), "dataType") : toPascalIdentifier(item.name(), "DataType")));
        String preferredRoot = camelCase ? "model" : "Model";
        String preferredElement = camelCase ? "element" : "Element";
        String root = uniqueRuleName(preferredRoot, camelCase ? "hydraModel" : "HydraModel", occupied);
        occupied.add(root);
        String element = uniqueRuleName(preferredElement, camelCase ? "hydraElement" : "HydraElement", occupied);
        return new AuxiliaryRuleNames(root, element);
    }

    /** Descripción: Selecciona un nombre de regla libre a partir de un nombre convencional y un fallback HYDRA, añadiendo un sufijo numérico solamente cuando ambos ya existen en el dominio. Entradas: preferred: nombre convencional; fallback: nombre alternativo; occupied: nombres ya utilizados. Salida: devuelve un identificador determinista no ocupado. */ private static String uniqueRuleName(String preferred, String fallback, Set<String> occupied) {
        if(!occupied.contains(preferred)) return preferred;
        if(!occupied.contains(fallback)) return fallback;
        for(int index = 2; index < 10_000; index++) {
            String candidate = fallback + index;
            if(!occupied.contains(candidate)) return candidate;
        }
        throw new IllegalStateException("Could not allocate a collision-free auxiliary grammar rule");
    }

    /** Descripción: Ejecuta la operación «splitWords» correspondiente a la responsabilidad de GrammarGenerationSupport. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> splitWords(String value) {
        if(value == null) {
            return List.of();
        }

        String normalized = value.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("[^A-Za-z0-9]+", " ").trim();

        if(normalized.isBlank()) {
            return List.of();
        }

        return List.of(normalized.split("\\s+"));
    }

    /** Descripción: Elimina o libera el recurso gestionado por «removeInvalidIdentifierCharacters» de forma controlada. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String removeInvalidIdentifierCharacters(String value) {
        StringBuilder result = new StringBuilder();

        for(int index = 0; index < value.length(); index += 1) {
            char character = value.charAt(index);

            if(Character.isJavaIdentifierPart(character)) {
                result.append(character);
            }
        }

        return result.isEmpty() ? "Generated" : result.toString();
    }
}
