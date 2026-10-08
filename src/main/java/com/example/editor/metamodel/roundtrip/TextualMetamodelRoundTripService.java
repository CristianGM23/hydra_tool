package com.example.editor.metamodel.roundtrip;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.export.DefaultNeutralMetamodelExportContext;
import com.example.editor.metamodel.export.LangiumMetamodelExporter;
import com.example.editor.metamodel.export.MetamodelExportFormat;
import com.example.editor.metamodel.export.MetamodelTextExporter;
import com.example.editor.metamodel.export.XtextMetamodelExporter;
import com.example.editor.metamodel.importing.support.GeneratedGrammarImportSupport;

/** Ejecuta exportación y reimportación determinista antes de declarar una proyección textual semánticamente segura. */
@Service
public class TextualMetamodelRoundTripService {
    private final XtextMetamodelExporter xtext;
    private final LangiumMetamodelExporter langium;
    private final MetamodelSemanticComparator comparator;

    /** Descripción: Inicializa el servicio de round-trip con los exportadores Xtext/Langium y el comparador semántico neutral. Entradas: xtext: exportador Xtext; langium: exportador Langium; comparator: comparador que evalúa la equivalencia tras reimportar. Salida: deja el servicio preparado para verificar ambos proveedores de forma determinista. */
    public TextualMetamodelRoundTripService(XtextMetamodelExporter xtext, LangiumMetamodelExporter langium, MetamodelSemanticComparator comparator) {
        this.xtext=xtext; this.langium=langium; this.comparator=comparator;
    }

    /** Descripción: Ejecuta exportación y reimportación del metamodelo con el proveedor solicitado y compara el resultado con la fuente canónica. Entradas: source: metamodelo canónico de partida; format: proveedor textual, limitado a XTEXT o LANGIUM. Salida: devuelve la gramática generada, el metamodelo reimportado, avisos de importación y el informe de equivalencia semántica. */
    public TextualMetamodelRoundTripResult verify(MetaModel source, MetamodelExportFormat format) {
        if(source == null) throw new IllegalArgumentException("Source metamodel is required");
        MetamodelTextExporter exporter = switch(format) {
            case XTEXT -> xtext;
            case LANGIUM -> langium;
            default -> throw new IllegalArgumentException("Semantic textual round-trip only supports XTEXT and LANGIUM");
        };
        var context = new DefaultNeutralMetamodelExportContext(source, null, null, java.util.List.of());
        String generated = exporter.export(context);
        String sourceFormat = format == MetamodelExportFormat.XTEXT ? "xtext" : "langium";
        var parsed = GeneratedGrammarImportSupport.parseTextual(generated, source.name(), "", sourceFormat);
        return new TextualMetamodelRoundTripResult(format, generated, parsed.metamodel(), parsed.warnings(), comparator.compare(source, parsed.metamodel()));
    }

    /** Descripción: Exige que el round-trip textual preserve el significado estructural y falla de forma cerrada ante cualquier pérdida detectada. Entradas: source: metamodelo canónico de partida; format: proveedor Xtext o Langium que debe verificarse. Salida: devuelve el resultado del round-trip únicamente cuando es semánticamente equivalente; en caso contrario lanza una excepción con las diferencias. */
    public TextualMetamodelRoundTripResult requireLossless(MetaModel source, MetamodelExportFormat format) {
        var result=verify(source,format);
        if(!result.equivalence().semanticallyEquivalent()) throw new IllegalStateException("Textual round-trip is lossy for "+format+": "+result.equivalence().differences());
        return result;
    }
}
