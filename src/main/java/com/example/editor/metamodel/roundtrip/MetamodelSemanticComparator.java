package com.example.editor.metamodel.roundtrip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;

/**
 * Compara significado y no representación. Los ids, provenance y posiciones de origen pueden
 * regenerarse durante Xtext/Langium y por ello nunca participan en la identidad semántica.
 */
@Component
public class MetamodelSemanticComparator {
    /**
     * Descripción: Compara dos metamodelos por su significado estructural, ignorando identificadores regenerables y metadatos de procedencia.
     * Entradas: expected: metamodelo canónico esperado; actual: metamodelo obtenido tras una proyección o reimportación.
     * Salida: devuelve el nivel de fidelidad semántica y el detalle de todas las diferencias detectadas.
     */
    public SemanticEquivalenceReport compare(MetaModel expected, MetaModel actual) {
        if(expected == null || actual == null) return new SemanticEquivalenceReport(SemanticFidelity.UNAVAILABLE,
                List.of(new SemanticDifference("$", "MODEL_UNAVAILABLE", expected == null ? "missing" : "present", actual == null ? "missing" : "present", true)));
        List<SemanticDifference> differences = new ArrayList<>();
        compareMaps("$.classes", classSignatures(expected), classSignatures(actual), differences, true);
        compareMaps("$.enums", enumSignatures(expected), enumSignatures(actual), differences, true);
        compareMaps("$.dataTypes", dataTypeSignatures(expected), dataTypeSignatures(actual), differences, true);
        compareMaps("$.relations", relationSignatures(expected), relationSignatures(actual), differences, true);
        compareRoot(expected, actual, differences);
        SemanticFidelity fidelity = differences.isEmpty() ? SemanticFidelity.EXACT
                : differences.stream().anyMatch(SemanticDifference::lossy) ? SemanticFidelity.LOSSY : SemanticFidelity.HIGH;
        return new SemanticEquivalenceReport(fidelity, differences);
    }

    /**
     * Descripción: Comprueba que una raíz semántica explícita siga apuntando a la misma clase después del round-trip.
     * Entradas: expected: modelo de referencia; actual: modelo reconstruido; differences: acumulador de diferencias semánticas.
     * Salida: no devuelve valor; añade una diferencia con pérdida cuando la raíz explícita cambia.
     */
    private void compareRoot(MetaModel expected, MetaModel actual, List<SemanticDifference> differences) {
        String left = rootName(expected);
        if(left.isBlank()) return; // no explicit canonical root: parser inference is representation metadata, not domain semantics
        String right = rootName(actual);
        if(!Objects.equals(left, right)) differences.add(new SemanticDifference("$.root", "ROOT_CHANGED", left, right, true));
    }

    /**
     * Descripción: Resuelve el nombre de la raíz semántica explícita usando anotaciones de clase o el identificador canónico del modelo.
     * Entradas: model: metamodelo cuyo root debe resolverse.
     * Salida: devuelve el nombre de la clase raíz o una cadena vacía si no existe una declaración explícita.
     */
    private String rootName(MetaModel model) {
        for(MetaClass item : model.classes()) if("true".equalsIgnoreCase(item.annotations().get("root")) || "true".equalsIgnoreCase(item.annotations().get("entryRule"))) return item.name();
        String rootId = model.annotations().getOrDefault("rootClassId", "");
        if(!rootId.isBlank()) for(MetaClass item : model.classes()) if(rootId.equals(item.id())) return item.name();
        return "";
    }

    /**
     * Descripción: Construye firmas semánticas deterministas para las clases, sus atributos y operaciones sin depender de ids internos.
     * Entradas: model: metamodelo del que se extraen las clases.
     * Salida: devuelve un mapa ordenado lógicamente por nombre de clase hacia su firma estructural comparable.
     */
    private Map<String,String> classSignatures(MetaModel model) {
        Map<String,String> result = new LinkedHashMap<>();
        for(MetaClass item : model.classes()) {
            List<String> attributes = item.attributes().stream().map(value -> attributeSignature(model, value)).sorted().toList();
            result.put(item.name(), item.kind() + "|attrs=" + attributes + "|ops=" + item.operations().stream().map(value -> value.name()).sorted().toList());
        }
        return result;
    }

    /**
     * Descripción: Codifica la semántica observable de un atributo mediante nombre, tipo resuelto y multiplicidad.
     * Entradas: model: metamodelo necesario para resolver tipos por nombre; value: atributo que se normaliza.
     * Salida: devuelve una firma textual determinista destinada exclusivamente a la comparación semántica.
     */
    private String attributeSignature(MetaModel model, MetaAttribute value) {
        return value.name()+":"+typeSignature(model,value.type())+":"+multiplicity(value.multiplicity());
    }

    /**
     * Descripción: Normaliza una referencia de tipo sustituyendo identificadores internos por nombres semánticos cuando pueden resolverse.
     * Entradas: model: metamodelo que contiene clases, enumeraciones y tipos de datos; value: referencia de tipo que se normaliza.
     * Salida: devuelve una firma estable con clase de referencia, destino semántico y nombre externo.
     */
    private String typeSignature(MetaModel model, MetaTypeReference value) {
        if(value == null || value.kind() == null) return "";
        String target = value.targetId();
        if(target != null && !target.isBlank()) {
            for(MetaClass item:model.classes()) if(target.equals(item.id())) target=item.name();
            for(MetaEnum item:model.enums()) if(target.equals(item.id())) target=item.name();
            for(MetaDataType item:model.dataTypes()) if(target.equals(item.id())) target=item.name();
        }
        return value.kind()+":"+(target == null ? "" : target)+":"+(value.externalName() == null ? "" : value.externalName());
    }

    /**
     * Descripción: Obtiene las firmas semánticas de las enumeraciones preservando el orden declarado de sus literales.
     * Entradas: model: metamodelo del que se leen las enumeraciones.
     * Salida: devuelve un mapa de nombre de enumeración a secuencia normalizada de literales.
     */
    private Map<String,String> enumSignatures(MetaModel model) {
        Map<String,String> result = new LinkedHashMap<>();
        for(MetaEnum item:model.enums()) result.put(item.name(), item.literals().stream().map(value -> value.name()).toList().toString());
        return result;
    }

    /**
     * Descripción: Obtiene la identidad semántica de los tipos de datos canónicos sin incorporar ids regenerables.
     * Entradas: model: metamodelo del que se leen los tipos de datos.
     * Salida: devuelve un mapa estable que representa cada tipo por su nombre semántico.
     */
    private Map<String,String> dataTypeSignatures(MetaModel model) {
        Map<String,String> result = new LinkedHashMap<>();
        for(MetaDataType item:model.dataTypes()) result.put(item.name(), item.name());
        return result;
    }

    /**
     * Descripción: Construye firmas para relaciones usando nombres de clases, clase de relación y multiplicidades de ambos extremos.
     * Entradas: model: metamodelo que contiene las relaciones y las clases referenciadas.
     * Salida: devuelve las relaciones normalizadas para poder comparar contenciones, referencias, herencia y agregaciones por significado.
     */
    private Map<String,String> relationSignatures(MetaModel model) {
        Map<String,String> result = new LinkedHashMap<>();
        Map<String,String> classNames = new LinkedHashMap<>();
        for(MetaClass item:model.classes()) classNames.put(item.id(), item.name());
        for(MetaRelation item:model.relations()) {
            String source=classNames.getOrDefault(item.sourceTypeId(), item.sourceTypeId());
            String target=classNames.getOrDefault(item.targetTypeId(), item.targetTypeId());
            String key=source+"."+item.name()+"->"+target;
            result.put(key,item.kind()+"|source="+multiplicity(item.sourceMultiplicity())+"|target="+multiplicity(item.targetMultiplicity()));
        }
        return result;
    }

    /**
     * Descripción: Normaliza una multiplicidad canónica a una representación estable de límite inferior y superior.
     * Entradas: value: multiplicidad que debe representarse.
     * Salida: devuelve la forma «inferior..superior», usando «*» para el límite no acotado, o vacío si no existe multiplicidad.
     */
    private String multiplicity(Multiplicity value) {
        if(value == null) return "";
        return value.lowerBound()+".."+(value.unbounded()?"*":value.upperBound());
    }

    /**
     * Descripción: Compara dos conjuntos normalizados de elementos y registra ausencias, adiciones o cambios de firma por una ruta semántica.
     * Entradas: path: ruta lógica de la colección; expected: firmas esperadas; actual: firmas reconstruidas; differences: acumulador de diferencias; lossy: indica si una divergencia implica pérdida de significado.
     * Salida: no devuelve valor; incorpora al acumulador una diferencia por cada clave cuyo significado no coincide.
     */
    private void compareMaps(String path, Map<String,String> expected, Map<String,String> actual, List<SemanticDifference> differences, boolean lossy) {
        Set<String> keys = new TreeSet<>(); keys.addAll(expected.keySet()); keys.addAll(actual.keySet());
        for(String key:keys) {
            String left=expected.get(key), right=actual.get(key);
            if(!Objects.equals(left,right)) differences.add(new SemanticDifference(path+"["+key+"]", left==null?"UNEXPECTED_ELEMENT":right==null?"MISSING_ELEMENT":"ELEMENT_CHANGED", left, right, lossy));
        }
    }
}
