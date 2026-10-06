package com.example.editor.definition;

import java.util.EnumSet;
import java.util.Set;
import com.example.editor.codegen.ModelCodeGenerationFeature;

/** Opciones aditivas y neutrales para enriquecer código fuente generado. */
public record CodeGenerationFeatureOptions(boolean generateTests, boolean generateGettersSetters,
        boolean generateComparison, boolean generateStringRepresentation, boolean generateEquality) {
    /** Descripción: Devuelve un perfil sin scaffolding adicional para conservar compatibilidad. Entradas: ninguna. Salida: perfil vacío. */
    /** Descripción: Ejecuta la operación «none» correspondiente a la responsabilidad de CodeGenerationFeatureOptions. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static CodeGenerationFeatureOptions none() { return new CodeGenerationFeatureOptions(false, false, false, false, false); }

    /** Descripción: Combina dos perfiles tomando como activada cualquier capacidad solicitada por uno de ellos. Entradas: perfil adicional. Salida: perfil combinado. */
    /** Descripción: Actualiza el estado gestionado por CodeGenerationFeatureOptions mediante la operación «merge». Entradas: other: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CodeGenerationFeatureOptions merge(CodeGenerationFeatureOptions other) {
        if(other == null) return this;
        return new CodeGenerationFeatureOptions(generateTests || other.generateTests,
                generateGettersSetters || other.generateGettersSetters,
                generateComparison || other.generateComparison,
                generateStringRepresentation || other.generateStringRepresentation,
                generateEquality || other.generateEquality);
    }

    /** Descripción: Convierte los booleanos del perfil en capacidades explícitas para validación genérica por destino. Entradas: ninguna. Salida: conjunto inmutable de capacidades solicitadas. */
    /** Descripción: Ejecuta la operación «requestedFeatures» correspondiente a la responsabilidad de CodeGenerationFeatureOptions. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Set<ModelCodeGenerationFeature> requestedFeatures() {
        EnumSet<ModelCodeGenerationFeature> requested = EnumSet.noneOf(ModelCodeGenerationFeature.class);
        if(generateTests) requested.add(ModelCodeGenerationFeature.TESTS);
        if(generateGettersSetters) requested.add(ModelCodeGenerationFeature.GETTERS_SETTERS);
        if(generateComparison) requested.add(ModelCodeGenerationFeature.COMPARISON);
        if(generateStringRepresentation) requested.add(ModelCodeGenerationFeature.STRING_REPRESENTATION);
        if(generateEquality) requested.add(ModelCodeGenerationFeature.EQUALITY);
        return Set.copyOf(requested);
    }
}
