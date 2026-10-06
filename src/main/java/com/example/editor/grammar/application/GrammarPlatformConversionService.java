package com.example.editor.grammar.application;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.springframework.stereotype.Service;
import com.example.editor.grammar.GrammarProperties;
import com.example.editor.grammar.core.*;
import com.example.editor.grammar.visualization.GrammarClassDiagramProjectionService;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.export.*;

/** Prepara conversiones Xtext/Langium/ANTLR sin mutar ni persistir el borrador original. */
@Service
public class GrammarPlatformConversionService {
    private static final String BACKUP_PREFIX="hydra.conversion.backup.";
    private static final String FINGERPRINT_PREFIX="hydra.conversion.fingerprint.";
    private final GrammarClassDiagramProjectionService projections;
    private final GrammarProperties properties;
    private final GrammarToolchainProfileService profiles;
    private final Map<GrammarTechnology,MetamodelTextExporter> exporters;

    /** Descripción: Inicializa el servicio de conversión con la proyección estricta, límites configurables, catálogo de toolchains y exportadores registrados. */
    /** Descripción: Inicializa una instancia de GrammarPlatformConversionService con sus dependencias y datos de entrada. Entradas: projections: dato de entrada necesario para la operación; properties: configuración que gobierna el comportamiento; profiles: archivo o contenido que debe procesarse; textExporters: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public GrammarPlatformConversionService(GrammarClassDiagramProjectionService projections,GrammarProperties properties,
            GrammarToolchainProfileService profiles,List<MetamodelTextExporter> textExporters){
        this.projections=Objects.requireNonNull(projections,"projections");this.properties=properties==null?new GrammarProperties():properties;this.profiles=Objects.requireNonNull(profiles,"profiles");
        EnumMap<GrammarTechnology,MetamodelTextExporter> map=new EnumMap<>(GrammarTechnology.class);
        for(MetamodelTextExporter exporter:textExporters==null?List.<MetamodelTextExporter>of():textExporters){
            GrammarTechnology technology=switch(exporter.format()){case XTEXT->GrammarTechnology.XTEXT;case LANGIUM->GrammarTechnology.LANGIUM;case ANTLR->GrammarTechnology.ANTLR;default->null;};
            if(technology!=null)map.put(technology,exporter);
        }
        for(GrammarTechnology technology:GrammarTechnology.values())if(!map.containsKey(technology))throw new IllegalStateException("Missing grammar exporter for "+technology);
        exporters=Map.copyOf(map);
    }

    /** Descripción: Prepara una conversión transaccional, conserva una copia exacta acotada del origen, regenera o restaura el destino, reimporta estrictamente el resultado y solo marca Apply como seguro cuando ambas huellas estructurales coinciden. */
    /** Descripción: Ejecuta la operación «prepare» correspondiente a la responsabilidad de GrammarPlatformConversionService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public GrammarPlatformConversionPreview prepare(GrammarPlatformConversionRequest request){
        GrammarDefinitionModel source=request.grammar();GrammarTechnology target=request.targetTechnology();
        if(source.technology()==target)throw new IllegalArgumentException("Source and target grammar technologies must differ");
        if(source.documents().isEmpty())throw new IllegalArgumentException("Grammar conversion requires at least one source document");
        MetaModel sourceModel=projections.inferMetamodelStrict(source,source.documents());
        String sourceFingerprint=GrammarSemanticFingerprint.fingerprint(sourceModel);
        GrammarRuleGraphAnalyzer.Analysis sourceGraph=GrammarRuleGraphAnalyzer.analyze(source);
        LinkedHashMap<String,String> annotations=new LinkedHashMap<>(source.annotations());
        annotations.put(BACKUP_PREFIX+source.technology().name(),encodeBackup(new Backup(source.entryDocumentId(),source.entryRule(),source.toolchainProfileId(),source.documents())));
        annotations.put(FINGERPRINT_PREFIX+source.technology().name(),sourceFingerprint);
        List<String> warnings=new ArrayList<>();
        List<GrammarSourceDocument> targetDocuments=null;String restoredEntryRule="";String restoredEntryDocument="";String restoredToolchain="";boolean exact=false;
        String encoded=annotations.getOrDefault(BACKUP_PREFIX+target.name(),"");String storedFingerprint=annotations.getOrDefault(FINGERPRINT_PREFIX+target.name(),"");
        if(!encoded.isBlank()&&sourceFingerprint.equals(storedFingerprint)){
            try{Backup backup=decodeBackup(encoded);targetDocuments=backup.documents();restoredEntryRule=backup.entryRule();restoredEntryDocument=backup.entryDocumentId();restoredToolchain=backup.toolchainProfileId();exact=true;}
            catch(RuntimeException exception){warnings.add("La copia exacta anterior de "+target+" no es válida o supera los límites configurados; HYDRA regenerará el destino desde el modelo neutral.");}
        }
        if(targetDocuments==null){String text=exporters.get(target).export(new DefaultNeutralMetamodelExportContext(sourceModel,null,null,List.of()));targetDocuments=List.of(generatedDocument(source,target,text));}
        String toolchain=restoredToolchain.isBlank()?profiles.require("",target).id():restoredToolchain;
        String entryDocument=restoredEntryDocument.isBlank()?targetDocuments.stream().filter(GrammarSourceDocument::entryPoint).map(GrammarSourceDocument::id).findFirst().orElse(targetDocuments.get(0).id()):restoredEntryDocument;
        GrammarDefinitionModel provisional=new GrammarDefinitionModel(source.schemaVersion(),source.id(),source.name(),target,source.authoringMode(),source.metamodelId(),toolchain,entryDocument,restoredEntryRule,targetDocuments,source.semanticBindings(),source.syntaxStyleDefinitionId(),source.settings(),annotations);
        GrammarRuleGraphAnalyzer.Analysis targetGraph=GrammarRuleGraphAnalyzer.analyze(provisional);
        String targetEntry=restoredEntryRule.isBlank()?targetGraph.entry().rule():restoredEntryRule;
        GrammarDefinitionModel converted=new GrammarDefinitionModel(provisional.schemaVersion(),provisional.id(),provisional.name(),provisional.technology(),provisional.authoringMode(),provisional.metamodelId(),provisional.toolchainProfileId(),provisional.entryDocumentId(),targetEntry,provisional.documents(),provisional.semanticBindings(),provisional.syntaxStyleDefinitionId(),provisional.settings(),provisional.annotations());
        MetaModel targetModel=projections.inferMetamodelStrict(converted,converted.documents());
        String targetFingerprint=GrammarSemanticFingerprint.fingerprint(targetModel);boolean equivalent=sourceFingerprint.equals(targetFingerprint);
        if(!equivalent)warnings.add("La reimportación de la gramática destino no reproduce exactamente la estructura neutral de origen. La aplicación queda bloqueada.");
        List<String> recursive=limitItems(targetGraph.recursions().stream().flatMap(item->item.rules().stream()).distinct().toList());
        List<String> left=limitItems(targetGraph.recursions().stream().filter(GrammarRuleGraphAnalyzer.Recursion::leftRecursive).flatMap(item->item.rules().stream()).distinct().toList());
        List<String> preserved=preserved(sourceModel);
        List<String> evidence=new ArrayList<>();evidence.add("Proyección estricta de origen completada sin usar LAST_KNOWN_GOOD.");evidence.add("Gramática destino reimportada mediante el importador neutral de "+target+".");evidence.add("Fingerprint origen: "+sourceFingerprint);evidence.add("Fingerprint destino: "+targetFingerprint);evidence.add(equivalent?"Equivalencia estructural demostrada.":"Equivalencia estructural NO demostrada; Apply bloqueado.");
        String sourceHash=GrammarHashing.sources(source.documents()),targetHash=GrammarHashing.sources(converted.documents());
        String trace=trace(source,target,sourceFingerprint,targetFingerprint,sourceHash,targetHash,sourceGraph,targetGraph,preserved,warnings,evidence,exact,equivalent);
        return new GrammarPlatformConversionPreview(UUID.randomUUID().toString(),source.technology(),target,converted,equivalent,exact,sourceFingerprint,targetFingerprint,sourceHash,targetHash,sourceGraph.entry().rule(),targetEntry,sourceGraph.entry().method(),targetGraph.entry().method(),sourceGraph.entry().confidence(),targetGraph.entry().confidence(),recursive,left,preserved,warnings,evidence,trace);
    }

    /** Descripción: Crea o prepara el resultado de «generatedDocument» dentro de GrammarPlatformConversionService. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private GrammarSourceDocument generatedDocument(GrammarDefinitionModel source,GrammarTechnology target,String content){
        String base=source.id().replaceAll("[^A-Za-z0-9_-]","-");if(base.isBlank())base="grammar";
        return switch(target){
            case LANGIUM -> new GrammarSourceDocument("main",base+".langium","langium",content,"",true,true);
            case XTEXT -> new GrammarSourceDocument("main","src/"+base+".xtext","xtext",content,"",true,true);
            case ANTLR -> new GrammarSourceDocument("main",base+".g4","antlr",content,"",true,true);
        };
    }

    /** Descripción: Ejecuta la operación «encodeBackup» correspondiente a la responsabilidad de GrammarPlatformConversionService. Entradas: backup: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String encodeBackup(Backup backup){
        long raw=backup.documents().stream().mapToLong(doc->doc.content().getBytes(StandardCharsets.UTF_8).length).sum();
        if(raw>properties.getMaximumConversionBackupExpandedBytes())throw new IllegalArgumentException("Grammar source backup exceeds configured expanded-size limit");
        try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(GZIPOutputStream gzip=new GZIPOutputStream(bytes);DataOutputStream out=new DataOutputStream(gzip)){out.writeInt(1);write(out,backup.entryDocumentId());write(out,backup.entryRule());write(out,backup.toolchainProfileId());out.writeInt(backup.documents().size());for(GrammarSourceDocument doc:backup.documents()){write(out,doc.id());write(out,doc.relativePath());write(out,doc.languageId());write(out,doc.content());out.writeBoolean(doc.entryPoint());out.writeBoolean(doc.generated());}}byte[] compressed=bytes.toByteArray();if(compressed.length>properties.getMaximumConversionBackupBytes())throw new IllegalArgumentException("Grammar source backup exceeds configured compressed-size limit");return Base64.getEncoder().encodeToString(compressed);}catch(IOException exception){throw new IllegalStateException("Could not encode grammar source backup",exception);}
    }

    /** Descripción: Ejecuta la operación «decodeBackup» correspondiente a la responsabilidad de GrammarPlatformConversionService. Entradas: encoded: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Backup decodeBackup(String encoded){
        if(encoded.length()>maximumBase64Characters())throw new IllegalArgumentException("Encoded grammar source backup exceeds configured limit");
        try{byte[] compressed=Base64.getDecoder().decode(encoded);if(compressed.length>properties.getMaximumConversionBackupBytes())throw new IllegalArgumentException("Compressed grammar source backup exceeds configured limit");CountingInputStream counted=new CountingInputStream(new ByteArrayInputStream(compressed),properties.getMaximumConversionBackupExpandedBytes());try(DataInputStream in=new DataInputStream(new GZIPInputStream(counted))){if(in.readInt()!=1)throw new IllegalArgumentException("Unsupported grammar source backup version");String entryDocument=read(in);String entryRule=read(in);String toolchain=read(in);int count=in.readInt();if(count<0||count>properties.getMaximumDocuments())throw new IllegalArgumentException("Invalid grammar source backup document count");List<GrammarSourceDocument> documents=new ArrayList<>();for(int index=0;index<count;index++)documents.add(new GrammarSourceDocument(read(in),read(in),read(in),read(in),"",in.readBoolean(),in.readBoolean()));if(in.read()!=-1)throw new IllegalArgumentException("Grammar source backup contains trailing data");return new Backup(entryDocument,entryRule,toolchain,documents);}}
        catch(IOException|IllegalArgumentException exception){throw new IllegalArgumentException("Invalid grammar source backup",exception);}
    }

    /** Descripción: Ejecuta la operación «maximumBase64Characters» correspondiente a la responsabilidad de GrammarPlatformConversionService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private int maximumBase64Characters(){long bytes=properties.getMaximumConversionBackupBytes();long chars=((bytes+2L)/3L)*4L;return (int)Math.min(Integer.MAX_VALUE,Math.max(16L,chars+16L));}
    /** Descripción: Actualiza el estado gestionado por GrammarPlatformConversionService mediante la operación «write». Entradas: out: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private static void write(DataOutputStream out,String value)throws IOException{byte[] bytes=(value==null?"":value).getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
    /** Descripción: Obtiene la información correspondiente a «read» dentro de GrammarPlatformConversionService. Entradas: in: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String read(DataInputStream in)throws IOException{int length=in.readInt();if(length<0||length>properties.getMaximumConversionBackupExpandedBytes())throw new IOException("Invalid grammar backup string length");byte[] bytes=in.readNBytes(length);if(bytes.length!=length)throw new EOFException("Truncated grammar backup entry");return new String(bytes,StandardCharsets.UTF_8);}
    /** Descripción: Ejecuta la operación «limitItems» correspondiente a la responsabilidad de GrammarPlatformConversionService. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private <T> List<T> limitItems(List<T> values){int maximum=properties.getMaximumConversionTraceItems();return values.size()<=maximum?List.copyOf(values):List.copyOf(values.subList(0,maximum));}
    /** Descripción: Ejecuta la operación «preserved» correspondiente a la responsabilidad de GrammarPlatformConversionService. Entradas: model: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> preserved(MetaModel model){return List.of(model.classes().size()+" clases",model.enums().size()+" enumeraciones",model.dataTypes().size()+" tipos de datos",model.classes().stream().mapToInt(item->item.attributes().size()).sum()+" atributos",model.relations().size()+" relaciones",model.classes().stream().mapToInt(item->item.operations().size()).sum()+" operaciones");}
    /**
     * Genera una traza humana y acotada de la conversión sin introducir estado ni duplicar
     * información semántica fuera de los límites configurados.
     */
    /** Descripción: Ejecuta la operación «trace» correspondiente a la responsabilidad de GrammarPlatformConversionService. Entradas: source: dato de entrada necesario para la operación; target: dato de entrada necesario para la operación; sourceFingerprint: dato de entrada necesario para la operación; targetFingerprint: dato de entrada necesario para la operación; sourceHash: dato de entrada necesario para la operación; targetHash: dato de entrada necesario para la operación; sourceGraph: dato de entrada necesario para la operación; targetGraph: dato de entrada necesario para la operación; preserved: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación; evidence: dato de entrada necesario para la operación; exact: dato de entrada necesario para la operación; equivalent: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String trace(
            GrammarDefinitionModel source,
            GrammarTechnology target,
            String sourceFingerprint,
            String targetFingerprint,
            String sourceHash,
            String targetHash,
            GrammarRuleGraphAnalyzer.Analysis sourceGraph,
            GrammarRuleGraphAnalyzer.Analysis targetGraph,
            List<String> preserved,
            List<String> warnings,
            List<String> evidence,
            boolean exact,
            boolean equivalent) {
        StringBuilder trace = new StringBuilder("# Trazabilidad de conversión de gramática\n\n");
        trace.append("- Origen: ").append(source.technology())
                .append("\n- Destino: ").append(target)
                .append("\n- Estrategia: ")
                .append(exact ? "restauración exacta de fuente anterior" : "regeneración desde modelo neutral")
                .append("\n- Equivalencia estructural: ").append(equivalent ? "SÍ" : "NO")
                .append("\n- SHA-256 fuentes origen: ").append(sourceHash)
                .append("\n- SHA-256 fuentes destino: ").append(targetHash)
                .append("\n- Fingerprint neutral origen: ").append(sourceFingerprint)
                .append("\n- Fingerprint neutral destino: ").append(targetFingerprint)
                .append("\n- Regla de entrada origen: ").append(sourceGraph.entry().rule())
                .append(" (").append(sourceGraph.entry().method()).append(")")
                .append("\n- Regla de entrada destino: ").append(targetGraph.entry().rule())
                .append(" (").append(targetGraph.entry().method()).append(")\n\n")
                .append("## Elementos preservados\n");
        for (String item : preserved) {
            trace.append("- ").append(item).append('\n');
        }

        appendRecursions(trace, targetGraph);
        trace.append("\n## Validación\n");
        for (String item : evidence) {
            trace.append("- ").append(item).append('\n');
        }
        if (!warnings.isEmpty()) {
            trace.append("\n## Advertencias\n");
            for (String item : warnings) {
                trace.append("- ").append(item).append('\n');
            }
        }
        return truncateTrace(trace.toString());
    }

    /** Descripción: Ejecuta la operación «appendRecursions» correspondiente a la responsabilidad de GrammarPlatformConversionService. Entradas: trace: dato de entrada necesario para la operación; targetGraph: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendRecursions(StringBuilder trace, GrammarRuleGraphAnalyzer.Analysis targetGraph) {
        trace.append("\n## Recursividad destino\n");
        if (targetGraph.recursions().isEmpty()) {
            trace.append("- No se han detectado ciclos recursivos.\n");
            return;
        }
        int shown = 0;
        for (var recursion : targetGraph.recursions()) {
            if (shown >= properties.getMaximumConversionTraceItems()) {
                trace.append("- … ciclos adicionales omitidos por el límite configurado.\n");
                break;
            }
            trace.append("- ").append(recursion.kind()).append(": ")
                    .append(String.join(" → ", recursion.cycle()));
            if (recursion.nativeSupported()) {
                trace.append(" (soportada nativamente)");
            }
            trace.append('\n');
            shown++;
        }
    }

    /** Descripción: Ejecuta la operación «truncateTrace» correspondiente a la responsabilidad de GrammarPlatformConversionService. Entradas: trace: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String truncateTrace(String trace) {
        int maximum = properties.getMaximumConversionTraceCharacters();
        if (trace.length() <= maximum) {
            return trace;
        }
        return trace.substring(0, Math.max(0, maximum - 80))
                + "\n\n… trazabilidad truncada por el límite configurado.\n";
    }

    private record Backup(String entryDocumentId,String entryRule,String toolchainProfileId,List<GrammarSourceDocument> documents){private Backup{documents=documents==null?List.of():List.copyOf(documents);}}
    private static final class CountingInputStream extends FilterInputStream {private final long maximum;private long count;/** Descripción: Inicializa una instancia de CountingInputStream con sus dependencias y datos de entrada. Entradas: in: dato de entrada necesario para la operación; maximum: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private CountingInputStream(InputStream in,long maximum){super(in);this.maximum=maximum;}/** Descripción: Obtiene la información correspondiente a «read» dentro de GrammarPlatformConversionService. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public int read()throws IOException{int value=super.read();if(value>=0)increment(1);return value;}/** Descripción: Obtiene la información correspondiente a «read» dentro de GrammarPlatformConversionService. Entradas: b: dato de entrada necesario para la operación; off: dato de entrada necesario para la operación; len: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ @Override public int read(byte[] b,int off,int len)throws IOException{int read=super.read(b,off,len);if(read>0)increment(read);return read;}/** Descripción: Ejecuta la operación «increment» correspondiente a la responsabilidad de GrammarPlatformConversionService. Entradas: delta: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void increment(long delta)throws IOException{count+=delta;if(count>maximum)throw new IOException("Expanded grammar source backup exceeds configured limit");}}
}
