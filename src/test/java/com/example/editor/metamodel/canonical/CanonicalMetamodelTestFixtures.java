package com.example.editor.metamodel.canonical;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;

/**
 * Crea modelos pequeños y válidos para las pruebas canónicas.
 */
public final class CanonicalMetamodelTestFixtures {

    private CanonicalMetamodelTestFixtures() {
    }

    public static MetaModel valid(String id) {
        return valid(id, "Model " + id, Map.of("test", "canonical"));
    }

    public static MetaModel valid(String id, String name, Map<String, String> annotations) {
        MetaAttribute attribute = new MetaAttribute("attribute:name", "name", MetaTypeReference.primitive(MetaPrimitiveType.STRING), Multiplicity.one(), true, true, "", Map.of());
        MetaClass metaClass = new MetaClass("class:root", "Root", MetaClassKind.CLASS, List.of(attribute), List.of(), Map.of());
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, id, name, List.of(metaClass), List.of(), List.of(), List.of(), annotations);
    }

    public static MetaModel validWithOrderedAnnotations(String id, boolean reverse) {
        LinkedHashMap<String, String> annotations = new LinkedHashMap<>();

        if (reverse) {
            annotations.put("second", "2");
            annotations.put("first", "1");
        } else {
            annotations.put("first", "1");
            annotations.put("second", "2");
        }

        return valid(id, "Ordered", annotations);
    }

    public static MetaModel invalid(String id) {
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, id, "", List.of(), List.of(), List.of(), List.of(), Map.of());
    }
}
