package com.example.editor.grammar.provider;

import java.util.List;
import java.util.Map;
import com.example.editor.grammar.core.*;
import com.example.editor.grammar.diagnostic.GrammarCodeAction;

/**
 * Single technology SPI. Xtext, Langium and ANTLR remain interchangeable providers of one HYDRA grammar contract.
 * Provider-specific tool APIs never cross this boundary.
 */
public interface GrammarProvider {
    /** Descripción: Ejecuta la operación «descriptor» correspondiente a la responsabilidad de GrammarProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ GrammarProviderDescriptor descriptor();
    /** Descripción: Ejecuta la operación «analyze» correspondiente a la responsabilidad de GrammarProvider. Entradas: grammar: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ GrammarAnalysisResult analyze(GrammarDefinitionModel grammar, GrammarAnalysisRequest request);
    /** Descripción: Crea o prepara el resultado de «generateProject» dentro de GrammarProvider. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ GeneratedGrammarProject generateProject(GrammarProjectGenerationRequest request);
    /** Descripción: Procesa la operación «runtimeDescriptor» coordinando las responsabilidades de GrammarProvider. Entradas: grammar: dato de entrada necesario para la operación; profile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ GrammarRuntimeDescriptor runtimeDescriptor(GrammarDefinitionModel grammar, GrammarToolchainProfile profile);
    /** Descripción: Ejecuta la operación «syntaxMetadata» correspondiente a la responsabilidad de GrammarProvider. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ GrammarSyntaxMetadata syntaxMetadata();

    /** Descripción: Ejecuta la operación «deriveSemanticContract» correspondiente a la responsabilidad de GrammarProvider. Entradas: grammar: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ default GrammarSemanticBindingMap deriveSemanticContract(GrammarDefinitionModel grammar, GrammarAnalysisRequest request) {
        return analyze(grammar, request).semanticCandidate();
    }

    /** Descripción: Ejecuta la operación «materializeRuntime» correspondiente a la responsabilidad de GrammarProvider. Entradas: grammar: dato de entrada necesario para la operación; profile: archivo o contenido que debe procesarse; project: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ default GrammarRuntimeBundle materializeRuntime(GrammarDefinitionModel grammar, GrammarToolchainProfile profile,
            GeneratedGrammarProject project) {
        return new GrammarRuntimeBundle(runtimeDescriptor(grammar, profile), Map.of(),
                Map.of("providerId", descriptor().id(), "providerVersion", descriptor().version()));
    }

    /** Descripción: Ejecuta la operación «codeActions» correspondiente a la responsabilidad de GrammarProvider. Entradas: grammar: dato de entrada necesario para la operación; request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ default List<GrammarCodeAction> codeActions(GrammarDefinitionModel grammar, GrammarAnalysisRequest request) {
        return analyze(grammar, request).codeActions();
    }

    /** Descripción: Evalúa la condición «supports» sin modificar el estado externo. Entradas: capability: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ default boolean supports(GrammarProviderCapability capability) { return descriptor().supports(capability); }
}
