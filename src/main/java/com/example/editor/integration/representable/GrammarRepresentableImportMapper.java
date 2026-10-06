package com.example.editor.integration.representable;

import java.util.Map;
import java.util.Set;

/** Shared grammar mapper for Xtext and Langium changes. */
abstract class GrammarRepresentableImportMapper extends AbstractCataloguedRepresentableMapper {
    /** Descripción: Inicializa una instancia de GrammarRepresentableImportMapper con sus dependencias y datos de entrada. Entradas: technology: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ protected GrammarRepresentableImportMapper(String technology){
        super(technology, Map.ofEntries(
                Map.entry("PARSERRULE", NeutralElementKind.GRAMMAR_RULE),
                Map.entry("TERMINALRULE", NeutralElementKind.TERMINAL),
                Map.entry("CROSSREFERENCE", NeutralElementKind.CROSS_REFERENCE),
                Map.entry("ENUMRULE", NeutralElementKind.ENUM),
                Map.entry("ASSIGNMENT", NeutralElementKind.ATTRIBUTE),
                Map.entry("ACTION", NeutralElementKind.OPERATION)),
                Set.of("PARSERRULE", "ACTION"));
    }
}
