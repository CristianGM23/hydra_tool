package com.example.editor.metamodel.roundtrip;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.export.MetamodelExportFormat;

/** Verifica en una sola operación la paridad semántica profunda Xtext/Langium. */
@Service
public class TextualProviderParityService {
    private final TextualMetamodelRoundTripService roundTrip;
    private final MetamodelSemanticComparator comparator;
    private final TextualSemanticCapabilityAnalyzer capabilityAnalyzer;

    /** Descripción: Inicializa la verificación de paridad con el round-trip común, el comparador neutral y el analizador de capacidad nativa. Entradas: roundTrip: servicio que proyecta y reimporta; comparator: comparador semántico; capabilityAnalyzer: analizador de requisitos de runtime textual. Salida: deja el servicio preparado para verificar ambos proveedores con un único contrato. */
    public TextualProviderParityService(TextualMetamodelRoundTripService roundTrip, MetamodelSemanticComparator comparator,
            TextualSemanticCapabilityAnalyzer capabilityAnalyzer) {
        this.roundTrip=roundTrip; this.comparator=comparator; this.capabilityAnalyzer=capabilityAnalyzer;
    }

    /** Descripción: Verifica la fidelidad de Xtext y Langium respecto a la fuente y compara además entre sí ambos metamodelos reconstruidos. Entradas: source: metamodelo canónico que debe conservarse. Salida: devuelve un informe con los dos round-trips, la equivalencia cruzada y la capacidad real del runtime nativo. */
    public TextualProviderParityReport verify(MetaModel source) {
        var xtext=roundTrip.verify(source, MetamodelExportFormat.XTEXT);
        var langium=roundTrip.verify(source, MetamodelExportFormat.LANGIUM);
        return new TextualProviderParityReport(xtext, langium,
                comparator.compare(xtext.reimportedMetamodel(), langium.reimportedMetamodel()), capabilityAnalyzer.analyze(source));
    }

    /** Descripción: Exige paridad semántica completa entre Xtext y Langium y aplica comportamiento fail-closed cuando alguna comparación diverge. Entradas: source: metamodelo canónico que se proyecta con ambos proveedores. Salida: devuelve el informe de paridad cuando todas las comparaciones son equivalentes; si no, lanza una excepción detallada. */
    public TextualProviderParityReport requireSemanticParity(MetaModel source) {
        var result=verify(source);
        if(!result.semanticallyAligned()) throw new IllegalStateException("Xtext/Langium semantic parity failed: xtext="+result.xtext().equivalence()+", langium="+result.langium().equivalence()+", cross="+result.crossProviderEquivalence());
        return result;
    }
}
