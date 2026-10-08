package com.example.editor.textual.roundtrip;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.export.MetamodelExportFormat;
import com.example.editor.validation.model.CanonicalModelDocument;

/** Verifica paridad de instancia entre las proyecciones Xtext y Langium sobre una única fuente canónica. */
@Service
public class TextualInstanceProviderParityService {
    private final TextualInstanceRoundTripService roundTrips;private final CanonicalModelSemanticComparator comparator;
    /** Descripción: Inicializa la comparación cruzada de proveedores. Entradas: roundTrips y comparator. Salida: deja el servicio listo para comparar Xtext y Langium. */ public TextualInstanceProviderParityService(TextualInstanceRoundTripService roundTrips,CanonicalModelSemanticComparator comparator){this.roundTrips=roundTrips;this.comparator=comparator;}
    /** Descripción: Ejecuta ambos round-trips con idéntica estrategia documental y compara las reconstrucciones canónicas entre sí. Entradas: metamodel, source y strategy. Salida: devuelve evidencia Xtext, Langium y equivalencia cruzada. */ public TextualInstanceProviderParityReport verify(MetaModel metamodel,CanonicalModelDocument source,TextualInstanceDocumentStrategy strategy){var xtext=roundTrips.verify(metamodel,source,MetamodelExportFormat.XTEXT,strategy);var langium=roundTrips.verify(metamodel,source,MetamodelExportFormat.LANGIUM,strategy);var cross=comparator.compare(metamodel,xtext.reconstructedModel(),langium.reconstructedModel());return new TextualInstanceProviderParityReport(xtext,langium,cross);}
    /** Descripción: Exige paridad semántica completa entre los dos proveedores y falla de forma cerrada ante divergencias. Entradas: metamodel, source y strategy. Salida: devuelve el informe únicamente si ambos recorridos son lossless y equivalentes. */ public TextualInstanceProviderParityReport requireSemanticParity(MetaModel metamodel,CanonicalModelDocument source,TextualInstanceDocumentStrategy strategy){var report=verify(metamodel,source,strategy);if(!report.semanticallyAligned())throw new IllegalStateException("Xtext/Langium instance parity failed: "+report.crossProviderEquivalence().differences());return report;}
}
