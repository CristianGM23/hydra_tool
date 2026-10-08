package com.example.editor.textual.roundtrip;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.editor.grammar.core.TextRange;
import com.example.editor.grammar.diagnostic.SourceTextIndex;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;
import com.example.editor.metamodel.export.GrammarGenerationSupport;
import com.example.editor.metamodel.export.MetamodelExportFormat;
import com.example.editor.metamodel.export.MetamodelExportSupport;
import com.example.editor.metamodel.roundtrip.TextualSemanticIssue;
import com.example.editor.textual.source.TextualModelSourceDocument;
import com.example.editor.textual.source.TextualSemanticSourceMapEntry;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;

/** Codec estructural para instancias de las gramáticas Xtext/Langium generadas por HYDRA. */
@Component
public class GeneratedTextualInstanceCodec {
    public record EncodeResult(List<TextualModelSourceDocument> documents, List<TextualInstanceDocumentDependency> dependencies, List<TextualSemanticIssue> issues, Map<String,String> metadata) {
        public EncodeResult { documents=documents==null?List.of():List.copyOf(documents);dependencies=dependencies==null?List.of():List.copyOf(dependencies);issues=issues==null?List.of():List.copyOf(issues);metadata=metadata==null?Map.of():Map.copyOf(metadata); }
        /** Descripción: Determina si la instancia puede materializarse sin introducir identidad o tipos sintéticos. Entradas: no requiere parámetros. Salida: devuelve true cuando no existe ninguna incidencia bloqueante. */ public boolean representable(){return issues.stream().noneMatch(TextualSemanticIssue::blocksNativeRuntime);}
    }
    public record DecodeResult(CanonicalModelDocument model, List<TextualSemanticSourceMapEntry> sourceMap, List<TextualInstanceDocumentDependency> dependencies, List<TextualSemanticIssue> issues, Map<String,String> metadata) {
        public DecodeResult { sourceMap=sourceMap==null?List.of():List.copyOf(sourceMap);dependencies=dependencies==null?List.of():List.copyOf(dependencies);issues=issues==null?List.of():List.copyOf(issues);metadata=metadata==null?Map.of():Map.copyOf(metadata); }
        /** Descripción: Determina si todos los documentos se reconstruyeron de forma segura. Entradas: no requiere parámetros. Salida: devuelve true cuando existe modelo y no hay incidencias bloqueantes. */ public boolean valid(){return model!=null&&issues.stream().noneMatch(TextualSemanticIssue::blocksNativeRuntime);}
    }

    /** Descripción: Serializa una instancia canónica con la sintaxis realmente emitida por los exportadores Xtext/Langium, preservando contenciones anidadas y referencias por identidad. Entradas: metamodel, model, format, strategy y revision. Salida: devuelve documentos portables o incidencias explícitas cuando el runtime nativo no puede representar la identidad sin inventarla. */
    public EncodeResult encode(MetaModel metamodel, CanonicalModelDocument model, MetamodelExportFormat format, TextualInstanceDocumentStrategy strategy, long revision) {
        requireProvider(format);Objects.requireNonNull(metamodel,"metamodel");Objects.requireNonNull(model,"model");
        TextualInstanceDocumentStrategy effective=strategy==null?TextualInstanceDocumentStrategy.SINGLE_DOCUMENT:strategy;
        ModelIndex index=new ModelIndex(metamodel,model);List<TextualSemanticIssue> issues=new ArrayList<>();
        validateRepresentability(index,issues);
        if(issues.stream().anyMatch(TextualSemanticIssue::blocksNativeRuntime))return new EncodeResult(List.of(),List.of(),issues,Map.of("provider",format.name(),"strategy",effective.name()));
        Map<String,List<String>> documentRoots=new LinkedHashMap<>();
        if(effective==TextualInstanceDocumentStrategy.SINGLE_DOCUMENT) documentRoots.put("model",model.rootElementIds());
        else for(String root:model.rootElementIds()) documentRoots.put(root,List.of(root));
        List<TextualModelSourceDocument> documents=new ArrayList<>();Set<String> emitted=new LinkedHashSet<>();List<TextualInstanceDocumentDependency> dependencies=new ArrayList<>();Map<String,String> ownerDocument=new LinkedHashMap<>();
        for(Map.Entry<String,List<String>> entry:documentRoots.entrySet()) for(String root:entry.getValue()) assignDocument(index,root,entry.getKey(),ownerDocument);
        for(Map.Entry<String,List<String>> entry:documentRoots.entrySet()) {
            StringBuilder text=new StringBuilder();
            for(String root:entry.getValue()){writeElement(index,root,text,0,emitted,issues);if(text.length()>0&&text.charAt(text.length()-1)!='\n')text.append('\n');}
            String id=effective==TextualInstanceDocumentStrategy.SINGLE_DOCUMENT?"model":entry.getKey();
            documents.add(new TextualModelSourceDocument(id,safePath(id)+"."+format.name().toLowerCase(Locale.ROOT)+".model",text.toString(),revision));
        }
        for(CanonicalModelRelation relation:model.relations()) if(index.isNonContainment(relation.relationId())) {String sourceDocument=ownerDocument.get(relation.sourceElementId()),targetDocument=ownerDocument.get(relation.targetElementId());if(!Objects.equals(sourceDocument,targetDocument))dependencies.add(new TextualInstanceDocumentDependency(sourceDocument,targetDocument,relation.sourceElementId(),relation.targetElementId(),relation.relationId(),"WORKSPACE_SCOPE"));}
        Map<String,String> metadata=new LinkedHashMap<>();metadata.put("provider",format.name());metadata.put("strategy",effective.name());metadata.put("documentCount",Integer.toString(documents.size()));metadata.put("crossDocumentReferences",Integer.toString(dependencies.size()));metadata.put("requiresWorkspaceIndex",Boolean.toString(!dependencies.isEmpty()));metadata.put("deepContainmentDepth",Integer.toString(maxDepth(index)));
        return new EncodeResult(documents,dependencies,issues,metadata);
    }

    /** Descripción: Reconstruye una instancia canónica desde uno o varios documentos generados, resolviendo referencias después de analizar todos los archivos para admitir destinos forward/backward y ciclos no contenedores. Entradas: metamodel, documents, format y metadatos del documento canónico. Salida: devuelve modelo, source map bidireccional e incidencias de resolución. */
    public DecodeResult decode(MetaModel metamodel,List<TextualModelSourceDocument> documents,MetamodelExportFormat format,String modelId,String metamodelEtag,String modelName) {
        requireProvider(format);Objects.requireNonNull(metamodel,"metamodel");List<TextualSemanticIssue> issues=new ArrayList<>();
        DecodeState state=new DecodeState(metamodel,format,issues);List<TextualModelSourceDocument> safeDocuments=documents==null?List.of():List.copyOf(documents);
        for(TextualModelSourceDocument document:safeDocuments){Parser parser=new Parser(document,state);while(parser.hasMore()){String root=parser.parseElement(null,null);if(root!=null&&!root.isBlank())state.roots.add(root);else if(parser.hasMore())parser.skipUnexpected();}}
        state.resolvePendingReferences();
        CanonicalModelDocument model=null;
        if(issues.stream().noneMatch(TextualSemanticIssue::blocksNativeRuntime)) model=new CanonicalModelDocument(null,modelId,metamodel.id(),metamodelEtag,modelName,List.copyOf(state.roots),List.copyOf(state.elements.values()),List.copyOf(state.relations),Map.of("source","TEXTUAL_GENERATED_ROUNDTRIP","provider",format.name(),"documents",Integer.toString(safeDocuments.size())));
        Map<String,String> metadata=new LinkedHashMap<>();metadata.put("provider",format.name());metadata.put("documentCount",Integer.toString(safeDocuments.size()));metadata.put("resolvedForwardBackwardReferences",Integer.toString(state.resolvedReferences));metadata.put("sourceMapEntries",Integer.toString(state.sourceMap.size()));
        return new DecodeResult(model,state.sourceMap,state.dependencies,issues,metadata);
    }

    /** Descripción: Valida que el proveedor solicitado sea uno de los dos destinos con paridad semántica profunda de R6C3. Entradas: format: formato textual. Salida: no devuelve valor y rechaza cualquier destino distinto de XTEXT o LANGIUM. */ private static void requireProvider(MetamodelExportFormat format){if(format!=MetamodelExportFormat.XTEXT&&format!=MetamodelExportFormat.LANGIUM)throw new IllegalArgumentException("Generated textual instance codec only supports XTEXT and LANGIUM");}
    /** Descripción: Comprueba que cada clase tenga identidad nativa y que los valores del atributo name coincidan con los IDs canónicos, además de filtrar primitivas que los exportadores aún proyectan como STRING. Entradas: index e issues. Salida: añade incidencias bloqueantes sin alterar la instancia. */ private static void validateRepresentability(ModelIndex index,List<TextualSemanticIssue> issues){
        Set<String> reserved=generatedKeywords(index.metamodel);validateGeneratedKeywordUniqueness(index.metamodel,issues);
        for(CanonicalModelElement element:index.model.elements()){
            MetaClass type=index.classes.get(element.classId());if(type==null){issues.add(issue("INSTANCE_CLASS_UNAVAILABLE",element.id(),"Class is not present in the metamodel"));continue;}
            if(!element.id().matches("[A-Za-z_][A-Za-z0-9_]*"))issues.add(issue("INSTANCE_ID_NOT_PORTABLE_ID",element.id(),"Element id is outside the portable Xtext/Langium ID subset [A-Za-z_][A-Za-z0-9_]*"));
            if(reserved.contains(element.id()))issues.add(issue("INSTANCE_ID_COLLIDES_WITH_KEYWORD",element.id(),"Element id collides with a keyword emitted by the generated grammar; exact portable native linking requires a non-keyword identity or an explicit provider-specific escaping policy"));
            MetaAttribute name=MetamodelExportSupport.findNameAttribute(type);if(name==null){issues.add(issue("INSTANCE_IDENTITY_REQUIRES_EXTERNAL_PROVIDER",element.id(),"Type '"+type.name()+"' has no canonical name attribute; exact native instance identity cannot be serialized without an external identity provider"));continue;}
            Object value=element.attributes().get(name.id());if(!(value instanceof String text)||!element.id().equals(text))issues.add(issue("INSTANCE_IDENTITY_MISMATCH",element.id(),"Canonical element id must equal its native name attribute for exact Xtext/Langium round-trip"));
            for(MetaAttribute attribute:type.attributes())if(attribute.type()!=null&&attribute.type().kind()==MetaTypeReferenceKind.PRIMITIVE&&Set.of("FLOAT","DOUBLE","DECIMAL","CHARACTER","DATE","BINARY","OBJECT").contains(attribute.type().targetId())&&element.attributes().containsKey(attribute.id()))issues.add(issue("INSTANCE_PRIMITIVE_REQUIRES_CUSTOM_VALUE_CONVERTER",element.id()+"."+attribute.id(),"Primitive '"+attribute.type().targetId()+"' is emitted through STRING by the generated grammar and needs a value converter for exact runtime typing"));
        }
    }
    /** Descripción: Reúne las palabras clave literales reservadas por clases, atributos, relaciones y enumeraciones de la gramática generada. Entradas: metamodel: contrato neutral. Salida: devuelve un conjunto estable usado para rechazar identidades que necesitarían escaping específico del proveedor. */ private static Set<String> generatedKeywords(MetaModel metamodel){Set<String> result=new LinkedHashSet<>();for(MetaClass type:metamodel.classes()){result.add(GrammarGenerationSupport.toKeyword(type.name()));for(MetaAttribute attribute:type.attributes())result.add(GrammarGenerationSupport.toKeyword(attribute.name()));}for(MetaRelation relation:metamodel.relations())if(relation.kind()==MetaRelationKind.REFERENCE||relation.kind()==MetaRelationKind.AGGREGATION||relation.kind()==MetaRelationKind.CONTAINMENT)result.add(GrammarGenerationSupport.toKeyword(relation.name().isBlank()?"reference":relation.name()));for(MetaEnum value:metamodel.enums())value.literals().forEach(literal->result.add(literal.name()));result.add("true");result.add("false");return Set.copyOf(result);}
    /** Descripción: Detecta keywords de clases o features que colisionan dentro de la sintaxis generada y que impedirían una decodificación portable no ambigua. Entradas: metamodel e issues. Salida: añade incidencias bloqueantes por cada colisión encontrada. */ private static void validateGeneratedKeywordUniqueness(MetaModel metamodel,List<TextualSemanticIssue> issues){Map<String,String> classKeywords=new LinkedHashMap<>();for(MetaClass type:metamodel.classes())if(type.kind()==MetaClassKind.CLASS){String keyword=GrammarGenerationSupport.toKeyword(type.name());String previous=classKeywords.putIfAbsent(keyword,type.id());if(previous!=null&&!previous.equals(type.id()))issues.add(issue("INSTANCE_CLASS_KEYWORD_COLLISION",keyword,"Multiple concrete classes map to the same generated keyword"));Map<String,String> featureKeywords=new LinkedHashMap<>();for(MetaAttribute attribute:type.attributes()){MetaAttribute name=MetamodelExportSupport.findNameAttribute(type);if(name!=null&&name.id().equals(attribute.id()))continue;String feature=GrammarGenerationSupport.toKeyword(attribute.name());String old=featureKeywords.putIfAbsent(feature,attribute.id());if(old!=null&&!old.equals(attribute.id()))issues.add(issue("INSTANCE_FEATURE_KEYWORD_COLLISION",type.id()+"."+feature,"Multiple attributes/relations map to the same generated feature keyword"));}for(MetaRelation relation:metamodel.relations())if(relation.sourceTypeId().equals(type.id())&&(relation.kind()==MetaRelationKind.REFERENCE||relation.kind()==MetaRelationKind.AGGREGATION||relation.kind()==MetaRelationKind.CONTAINMENT)){String feature=GrammarGenerationSupport.toKeyword(relation.name().isBlank()?"reference":relation.name());String old=featureKeywords.putIfAbsent(feature,relation.id());if(old!=null&&!old.equals(relation.id()))issues.add(issue("INSTANCE_FEATURE_KEYWORD_COLLISION",type.id()+"."+feature,"Multiple attributes/relations map to the same generated feature keyword"));}}}
    /** Descripción: Crea una incidencia bloqueante de representabilidad con código estable. Entradas: code, path y message. Salida: devuelve la incidencia lista para el informe. */ private static TextualSemanticIssue issue(String code,String path,String message){return new TextualSemanticIssue(code,path,message,true);}
    /** Descripción: Asigna recursivamente a un documento la raíz y todos sus descendientes de contención. Entradas: index, elementId, documentId y ownerDocument. Salida: completa el mapa de pertenencia documental. */ private static void assignDocument(ModelIndex index,String elementId,String documentId,Map<String,String> ownerDocument){String previous=ownerDocument.putIfAbsent(elementId,documentId);if(previous!=null)return;for(CanonicalModelRelation relation:index.containmentsFrom(elementId))assignDocument(index,relation.targetElementId(),documentId,ownerDocument);}
    /** Descripción: Escribe un elemento y su árbol de contención siguiendo palabras clave, identidad y orden de features de la gramática generada. Entradas: index, elementId, buffer, indent, emitted e issues. Salida: añade texto determinista al buffer. */ private static void writeElement(ModelIndex index,String elementId,StringBuilder out,int indent,Set<String> emitted,List<TextualSemanticIssue> issues){
        if(!emitted.add(elementId)){issues.add(issue("CONTAINMENT_REEMISSION",elementId,"Element would be emitted more than once through containment"));return;}CanonicalModelElement element=index.elements.get(elementId);if(element==null){issues.add(issue("ELEMENT_NOT_FOUND",elementId,"Contained element is not present"));return;}MetaClass type=index.classes.get(element.classId());if(type==null)return;MetaAttribute name=MetamodelExportSupport.findNameAttribute(type);
        lineIndent(out,indent);out.append(GrammarGenerationSupport.toKeyword(type.name())).append(' ').append(element.id()).append(" {\n");
        for(MetaAttribute attribute:type.attributes())if(name==null||!attribute.id().equals(name.id())){Object value=element.attributes().get(attribute.id());lineIndent(out,indent+1);out.append(GrammarGenerationSupport.toKeyword(attribute.name()));if(value!=null){out.append(' ');writeAttributeValues(out,attribute,value);}out.append('\n');}
        for(MetaRelation declaration:index.structuralRelationsFrom(type.id())){List<CanonicalModelRelation> values=index.relationsFrom(elementId,declaration.id());lineIndent(out,indent+1);out.append(GrammarGenerationSupport.toKeyword(declaration.name().isBlank()?"reference":declaration.name()));if(declaration.kind()==MetaRelationKind.CONTAINMENT){out.append('\n');for(CanonicalModelRelation relation:values)writeElement(index,relation.targetElementId(),out,indent+2,emitted,issues);}else{if(!values.isEmpty())out.append(' ');for(int i=0;i<values.size();i++){if(i>0)out.append(' ');out.append(values.get(i).targetElementId());}out.append('\n');}}
        lineIndent(out,indent);out.append('}').append('\n');
    }
    /** Descripción: Serializa uno o varios valores de atributo según multiplicidad y tipo neutral. Entradas: out, attribute y value. Salida: añade la representación textual sin palabra clave. */ private static void writeAttributeValues(StringBuilder out,MetaAttribute attribute,Object value){List<?> values=value instanceof List<?> list?list:List.of(value);for(int i=0;i<values.size();i++){if(i>0)out.append(' ');out.append(serializeLiteral(attribute,values.get(i)));}}
    /** Descripción: Convierte un valor canónico a literal aceptado por las gramáticas generadas. Entradas: attribute y value. Salida: devuelve literal escapado o token primitivo. */ private static String serializeLiteral(MetaAttribute attribute,Object value){if(attribute.type()!=null&&attribute.type().kind()==MetaTypeReferenceKind.PRIMITIVE){String primitive=attribute.type().targetId();if("BOOLEAN".equals(primitive)||Set.of("BYTE","SHORT","INTEGER","LONG","BIG_INTEGER").contains(primitive))return String.valueOf(value);}if(attribute.type()!=null&&attribute.type().kind()==MetaTypeReferenceKind.ENUM)return String.valueOf(value);return quote(String.valueOf(value));}
    /** Descripción: Escapa un literal de texto de forma determinista. Entradas: value: texto sin escapar. Salida: devuelve una cadena entre comillas con barras, comillas y saltos protegidos. */ private static String quote(String value){return "\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";}
    /** Descripción: Añade espacios de indentación de dos columnas por nivel. Entradas: out e indent. Salida: modifica el buffer únicamente con espacios. */ private static void lineIndent(StringBuilder out,int indent){out.append("  ".repeat(Math.max(0,indent)));}
    /** Descripción: Construye una ruta relativa segura a partir de un ID sin aceptar navegación de directorios. Entradas: id: identificador de documento. Salida: devuelve un basename portable no vacío. */ private static String safePath(String id){String value=id==null?"model":id.replaceAll("[^A-Za-z0-9_.-]","_").replace("..","_");return value.isBlank()?"model":value;}
    /** Descripción: Calcula la profundidad máxima del árbol de contención para evidenciar que el round-trip cubre anidación profunda. Entradas: index. Salida: devuelve cero para modelo vacío o el número máximo de niveles alcanzados. */ private static int maxDepth(ModelIndex index){int max=0;for(String root:index.model.rootElementIds())max=Math.max(max,depth(index,root,new LinkedHashSet<>()));return max;}
    /** Descripción: Calcula profundidad de contención con protección frente a ciclos. Entradas: index, id y visiting. Salida: devuelve profundidad alcanzable desde el elemento. */ private static int depth(ModelIndex index,String id,Set<String> visiting){if(!visiting.add(id))return 0;int max=1;for(CanonicalModelRelation relation:index.containmentsFrom(id))max=Math.max(max,1+depth(index,relation.targetElementId(),visiting));visiting.remove(id);return max;}

    private static final class ModelIndex {
        private final MetaModel metamodel;private final CanonicalModelDocument model;private final Map<String,MetaClass> classes=new LinkedHashMap<>();private final Map<String,CanonicalModelElement> elements=new LinkedHashMap<>();private final Map<String,MetaRelation> relationDeclarations=new LinkedHashMap<>();private final Map<String,List<CanonicalModelRelation>> bySourceAndRelation=new LinkedHashMap<>();
        /** Descripción: Indexa metamodelo e instancia para serialización determinista. Entradas: metamodel y model. Salida: deja disponibles clases, elementos y relaciones por clave estable. */ private ModelIndex(MetaModel metamodel,CanonicalModelDocument model){this.metamodel=metamodel;this.model=model;metamodel.classes().forEach(item->classes.put(item.id(),item));model.elements().forEach(item->elements.put(item.id(),item));metamodel.relations().forEach(item->relationDeclarations.put(item.id(),item));for(CanonicalModelRelation relation:model.relations())bySourceAndRelation.computeIfAbsent(key(relation.sourceElementId(),relation.relationId()),ignored->new ArrayList<>()).add(relation);}
        /** Descripción: Obtiene relaciones estructurales directas de una clase en orden del metamodelo. Entradas: classId. Salida: devuelve referencia, agregación y contención. */ private List<MetaRelation> structuralRelationsFrom(String classId){return metamodel.relations().stream().filter(item->item.sourceTypeId().equals(classId)&&(item.kind()==MetaRelationKind.REFERENCE||item.kind()==MetaRelationKind.AGGREGATION||item.kind()==MetaRelationKind.CONTAINMENT)).toList();}
        /** Descripción: Obtiene instancias de una relación concreta desde un elemento. Entradas: sourceId y relationId. Salida: devuelve la lista preservando orden. */ private List<CanonicalModelRelation> relationsFrom(String sourceId,String relationId){return bySourceAndRelation.getOrDefault(key(sourceId,relationId),List.of());}
        /** Descripción: Obtiene únicamente las contenciones salientes de un elemento. Entradas: sourceId. Salida: devuelve relaciones de contención en orden canónico. */ private List<CanonicalModelRelation> containmentsFrom(String sourceId){List<CanonicalModelRelation> result=new ArrayList<>();for(MetaRelation declaration:metamodel.relations())if(declaration.kind()==MetaRelationKind.CONTAINMENT)result.addAll(relationsFrom(sourceId,declaration.id()));return result;}
        /** Descripción: Determina si una declaración representa una relación no contenedora serializable. Entradas: relationId. Salida: devuelve true para referencia o agregación. */ private boolean isNonContainment(String relationId){MetaRelation relation=relationDeclarations.get(relationId);return relation!=null&&(relation.kind()==MetaRelationKind.REFERENCE||relation.kind()==MetaRelationKind.AGGREGATION);}
        /** Descripción: Construye la clave interna source+relation sin colisiones léxicas. Entradas: source y relation. Salida: devuelve clave estable. */ private static String key(String source,String relation){return source+"\u0000"+relation;}
    }

    private static final class DecodeState {
        private final MetaModel metamodel;private final MetamodelExportFormat format;private final List<TextualSemanticIssue> issues;private final Map<String,MetaClass> classesById=new LinkedHashMap<>();private final Map<String,MetaClass> classesByKeyword=new LinkedHashMap<>();private final Map<String,CanonicalModelElement> elements=new LinkedHashMap<>();private final List<CanonicalModelRelation> relations=new ArrayList<>();private final List<PendingReference> pending=new ArrayList<>();private final List<TextualSemanticSourceMapEntry> sourceMap=new ArrayList<>();private final List<TextualInstanceDocumentDependency> dependencies=new ArrayList<>();private final List<String> roots=new ArrayList<>();private int resolvedReferences;
        /** Descripción: Prepara índices de clases y detecta colisiones de palabras clave antes de analizar documentos. Entradas: metamodel, format e issues. Salida: inicializa el estado global compartido por todos los archivos. */ private DecodeState(MetaModel metamodel,MetamodelExportFormat format,List<TextualSemanticIssue> issues){this.metamodel=metamodel;this.format=format;this.issues=issues;for(MetaClass item:metamodel.classes()){classesById.put(item.id(),item);if(item.kind()==MetaClassKind.CLASS){String keyword=GrammarGenerationSupport.toKeyword(item.name());MetaClass previous=classesByKeyword.putIfAbsent(keyword,item);if(previous!=null&&!previous.id().equals(item.id()))issues.add(issue("AMBIGUOUS_CLASS_KEYWORD",keyword,"Multiple classes map to the same generated keyword"));}}}
        /** Descripción: Resuelve referencias no contenedoras después de haber leído todos los documentos, permitiendo referencias forward, backward y ciclos. Entradas: no requiere parámetros. Salida: añade relaciones resueltas o incidencias bloqueantes y completa el source map. */ private void resolvePendingReferences(){for(PendingReference pendingReference:pending){CanonicalModelElement target=elements.get(pendingReference.targetId());if(target==null){issues.add(issue("REFERENCE_TARGET_NOT_FOUND",pendingReference.sourceId()+"."+pendingReference.relation().id(),"Target '"+pendingReference.targetId()+"' was not found in the complete multi-document model"));continue;}if(!isAssignable(target.classId(),pendingReference.relation().targetTypeId(),new LinkedHashSet<>())){issues.add(issue("REFERENCE_TARGET_TYPE_MISMATCH",pendingReference.sourceId()+"."+pendingReference.relation().id(),"Target class does not satisfy the declared relation type"));continue;}CanonicalModelRelation relation=new CanonicalModelRelation(relationId(pendingReference.sourceId(),pendingReference.relation().id(),pendingReference.targetId(),relations.size()),pendingReference.relation().id(),pendingReference.sourceId(),pendingReference.targetId(),Map.of("source","TEXTUAL_GENERATED_ROUNDTRIP","kind",pendingReference.relation().kind().name()));relations.add(relation);sourceMap.add(new TextualSemanticSourceMapEntry(relation.id(),"MODEL_RELATION",pendingReference.document().id(),pendingReference.range(),pendingReference.relation().id(),ruleId(pendingReference.relation().sourceTypeId()),Map.of("sourceElementId",pendingReference.sourceId(),"targetElementId",pendingReference.targetId(),"relationKind",pendingReference.relation().kind().name(),"provider",format.name())));String targetDocument=target.annotations().getOrDefault("documentId","");if(!Objects.equals(pendingReference.document().id(),targetDocument))dependencies.add(new TextualInstanceDocumentDependency(pendingReference.document().id(),targetDocument,pendingReference.sourceId(),pendingReference.targetId(),pendingReference.relation().id(),"WORKSPACE_SCOPE"));resolvedReferences++;}}
        /** Descripción: Comprueba asignabilidad transitiva para referencias y contenciones reconstruidas. Entradas: candidateId, targetId y visited. Salida: devuelve true para coincidencia directa o herencia/implementación válida. */ private boolean isAssignable(String candidateId,String targetId,Set<String> visited){if(Objects.equals(candidateId,targetId))return true;if(!visited.add(candidateId))return false;for(MetaRelation relation:metamodel.relations())if(relation.sourceTypeId().equals(candidateId)&&(relation.kind()==MetaRelationKind.INHERITANCE||relation.kind()==MetaRelationKind.IMPLEMENTATION)&&isAssignable(relation.targetTypeId(),targetId,visited))return true;return false;}
        /** Descripción: Produce un identificador de regla de trazabilidad coherente entre Xtext y Langium. Entradas: classId. Salida: devuelve provider:nombreRegla. */ private String ruleId(String classId){MetaClass type=classesById.get(classId);return format.name()+":"+(type==null?classId:GrammarGenerationSupport.toPascalIdentifier(type.name(),"Element"));}
    }

    private static final class Parser {
        private final TextualModelSourceDocument document;private final DecodeState state;private final SourceTextIndex textIndex;private final List<Token> tokens;private int position;
        /** Descripción: Tokeniza un documento y lo enlaza al estado multiarchivo compartido. Entradas: document y state. Salida: inicializa cursor y mapa de offsets. */ private Parser(TextualModelSourceDocument document,DecodeState state){this.document=document;this.state=state;this.textIndex=new SourceTextIndex(document.content());this.tokens=tokenize(document.content());}
        /** Descripción: Determina si quedan tokens semánticos por analizar. Entradas: no requiere parámetros. Salida: devuelve true mientras el cursor no esté al final. */ private boolean hasMore(){return position<tokens.size();}
        /** Descripción: Avanza un token cuando no es posible reconocer una construcción y registra una incidencia para garantizar progreso. Entradas: no requiere parámetros. Salida: mueve el cursor como máximo una posición. */ private void skipUnexpected(){if(hasMore()){Token token=tokens.get(position++);state.issues.add(issue("UNEXPECTED_TOKEN",document.id()+":"+token.start(),"Unexpected token '"+token.text()+"' while decoding generated instance syntax"));}}
        /** Descripción: Analiza recursivamente un elemento, sus atributos y contenciones, dejando referencias no contenedoras pendientes hasta leer todos los archivos. Entradas: expectedTarget y containmentContext opcionales. Salida: devuelve el ID del elemento reconstruido o null ante error bloqueante. */
        private String parseElement(MetaClass expectedTarget,ContainmentContext containmentContext){
            if(!hasMore())return null;
            Token classToken=take();
            MetaClass type=state.classesByKeyword.get(classToken.text());
            if(type==null){
                state.issues.add(issue("UNKNOWN_CLASS_KEYWORD",document.id()+":"+classToken.start(),"Unknown class keyword '"+classToken.text()+"'"));
                return null;
            }
            if(expectedTarget!=null&&!state.isAssignable(type.id(),expectedTarget.id(),new LinkedHashSet<>()))state.issues.add(issue("CONTAINMENT_TARGET_TYPE_MISMATCH",document.id()+":"+classToken.start(),"Contained type '"+type.name()+"' is not assignable to '"+expectedTarget.name()+"'"));
            MetaAttribute name=MetamodelExportSupport.findNameAttribute(type);
            if(name==null){
                state.issues.add(issue("INSTANCE_IDENTITY_REQUIRES_EXTERNAL_PROVIDER",type.id(),"Generated grammar type has no native identity attribute"));
                return null;
            }
            Token idToken=takeRequired("element identity");
            if(idToken==null)return null;
            String id=idToken.text();
            if(state.elements.containsKey(id)){
                state.issues.add(issue("DUPLICATE_ELEMENT_ID",id,"Element id is duplicated across textual documents"));
                return id;
            }
            Token open=takeRequired("{");
            if(open==null||!"{".equals(open.text())){
                state.issues.add(issue("EXPECTED_OPEN_BRACE",id,"Expected '{' after element identity"));
                return id;
            }
            Map<String,Object> attributes=new LinkedHashMap<>();
            attributes.put(name.id(),id);
            state.sourceMap.add(new TextualSemanticSourceMapEntry(id+":"+name.id(),"MODEL_FEATURE",document.id(),textIndex.range(idToken.start(),idToken.end()),name.id(),state.ruleId(type.id()),Map.of("ownerId",id,"identity","true","provider",state.format.name())));
            int start=classToken.start();
            List<MetaRelation> structural=state.metamodel.relations().stream().filter(item->item.sourceTypeId().equals(type.id())&&(item.kind()==MetaRelationKind.REFERENCE||item.kind()==MetaRelationKind.AGGREGATION||item.kind()==MetaRelationKind.CONTAINMENT)).toList();
            Map<String,MetaAttribute> attrsByKeyword=new LinkedHashMap<>();
            for(MetaAttribute attribute:type.attributes())if(!attribute.id().equals(name.id()))attrsByKeyword.put(GrammarGenerationSupport.toKeyword(attribute.name()),attribute);
            Map<String,MetaRelation> relsByKeyword=new LinkedHashMap<>();
            for(MetaRelation relation:structural)relsByKeyword.put(GrammarGenerationSupport.toKeyword(relation.name().isBlank()?"reference":relation.name()),relation);
            while(hasMore()&&!peekText("}")){
                Token feature=take();
                MetaAttribute attribute=attrsByKeyword.get(feature.text());
                MetaRelation relation=relsByKeyword.get(feature.text());
                if(attribute!=null){
                    List<ValueToken> values=parseAttributeValues(attribute,attrsByKeyword.keySet(),relsByKeyword.keySet());
                    if(!values.isEmpty()){
                        Object canonical=values.size()==1&&!isMany(attribute)?values.get(0).value():values.stream().map(ValueToken::value).toList();
                        attributes.put(attribute.id(),canonical);
                    }
                    for(ValueToken value:values)state.sourceMap.add(new TextualSemanticSourceMapEntry(id+":"+attribute.id(),"MODEL_FEATURE",document.id(),textIndex.range(value.token().start(),value.token().end()),attribute.id(),state.ruleId(type.id()),Map.of("ownerId",id,"provider",state.format.name())));
                }
                else if(relation!=null){
                    if(relation.kind()==MetaRelationKind.CONTAINMENT){
                        int count=0;
                        while(hasMore()&&!peekText("}")&&!isFeature(peek().text(),attrsByKeyword.keySet(),relsByKeyword.keySet())){
                            int childStart=peek().start();
                            MetaClass target=state.classesById.get(relation.targetTypeId());
                            String child=parseElement(target,new ContainmentContext(id,relation));
                            if(child==null)break;
                            CanonicalModelRelation instance=new CanonicalModelRelation(relationId(id,relation.id(),child,state.relations.size()),relation.id(),id,child,Map.of("source","TEXTUAL_GENERATED_ROUNDTRIP","kind","CONTAINMENT"));
                            state.relations.add(instance);
                            state.sourceMap.add(new TextualSemanticSourceMapEntry(instance.id(),"MODEL_RELATION",document.id(),textIndex.range(childStart,previousEnd()),relation.id(),state.ruleId(type.id()),Map.of("sourceElementId",id,"targetElementId",child,"relationKind","CONTAINMENT","provider",state.format.name())));
                            count++;
                        }
                        if(count==0&&relation.targetMultiplicity()!=null&&relation.targetMultiplicity().lowerBound()>0)state.issues.add(issue("EMPTY_CONTAINMENT_FEATURE",id+"."+relation.id(),"Required containment keyword was present without a child element"));
                    }
                    else{
                        List<Token> refs=parseReferenceIds(relation,attrsByKeyword.keySet(),relsByKeyword.keySet());
                        if(refs.isEmpty()&&relation.targetMultiplicity()!=null&&relation.targetMultiplicity().lowerBound()>0)state.issues.add(issue("EMPTY_REFERENCE_FEATURE",id+"."+relation.id(),"Required reference keyword was present without a target identity"));
                        for(Token ref:refs)state.pending.add(new PendingReference(id,relation,ref.text(),document,textIndex.range(ref.start(),ref.end())));
                    }
                }
                else state.issues.add(issue("UNKNOWN_FEATURE_KEYWORD",id+"."+feature.text(),"Keyword is not a generated attribute or relation of type '"+type.name()+"'"));
            }
            Token close=takeRequired("}");
            int end=close==null?previousEnd():close.end();
            CanonicalModelElement element=new CanonicalModelElement(id,type.id(),attributes,Map.of("documentId",document.id(),"grammarType",classToken.text(),"provider",state.format.name()));
            state.elements.put(id,element);
            state.sourceMap.add(new TextualSemanticSourceMapEntry(id,"MODEL_ELEMENT",document.id(),textIndex.range(start,end),type.id(),state.ruleId(type.id()),Map.of("type",classToken.text(),"provider",state.format.name())));
            return id;
        }
        /** Descripción: Analiza valores de un atributo hasta completar multiplicidad o alcanzar la siguiente feature. Entradas: attribute y conjuntos de keywords. Salida: devuelve valores tipados junto con sus tokens fuente. */ private List<ValueToken> parseAttributeValues(MetaAttribute attribute,Set<String> attrKeywords,Set<String> relationKeywords){List<ValueToken> values=new ArrayList<>();int max=isMany(attribute)?Integer.MAX_VALUE:1;while(hasMore()&&values.size()<max&&!peekText("}")&&!isFeature(peek().text(),attrKeywords,relationKeywords)){Token token=take();values.add(new ValueToken(token,parseLiteral(attribute,token)));}if(values.isEmpty()&&attribute.multiplicity()!=null&&attribute.multiplicity().lowerBound()>0)state.issues.add(issue("ATTRIBUTE_VALUE_MISSING",attribute.id(),"Required attribute keyword was present without a value"));return values;}
        /** Descripción: Analiza IDs de referencia respetando la multiplicidad declarada y consume siempre el primer destino aunque su ID coincida léxicamente con una keyword. Entradas: relation y keywords de atributos y relaciones. Salida: devuelve tokens de destino preservando orden. */ private List<Token> parseReferenceIds(MetaRelation relation,Set<String> attrKeywords,Set<String> relationKeywords){List<Token> result=new ArrayList<>();if(!hasMore()||peekText("}"))return result;boolean required=relation.targetMultiplicity()!=null&&relation.targetMultiplicity().lowerBound()>0;if(!required&&isFeature(peek().text(),attrKeywords,relationKeywords))return result;result.add(take());boolean many=relation.targetMultiplicity()!=null&&(relation.targetMultiplicity().unbounded()||relation.targetMultiplicity().upperBound()>1);if(!many)return result;while(hasMore()&&!peekText("}")&&!isFeature(peek().text(),attrKeywords,relationKeywords)){result.add(take());}return result;}
        /** Descripción: Convierte un token a valor canónico usando el tipo declarado del atributo. Entradas: attribute y token. Salida: devuelve String, Boolean o número integral con tipo estable. */ private Object parseLiteral(MetaAttribute attribute,Token token){String raw=token.text();if(attribute.type()!=null&&attribute.type().kind()==MetaTypeReferenceKind.PRIMITIVE){String primitive=attribute.type().targetId();try{return switch(primitive){case "BOOLEAN"->Boolean.valueOf(raw);case "BYTE"->Byte.valueOf(raw);case "SHORT"->Short.valueOf(raw);case "INTEGER"->Integer.valueOf(raw);case "LONG"->Long.valueOf(raw);case "BIG_INTEGER"->new java.math.BigInteger(raw);default->raw;};}catch(RuntimeException failure){state.issues.add(issue("ATTRIBUTE_LITERAL_TYPE_MISMATCH",attribute.id(),"Value '"+raw+"' cannot be converted to "+primitive));return raw;}}return raw;}
        /** Descripción: Determina si un atributo admite varios valores. Entradas: attribute. Salida: devuelve true cuando la multiplicidad superior es mayor que uno o ilimitada. */ private static boolean isMany(MetaAttribute attribute){return attribute.multiplicity()!=null&&(attribute.multiplicity().unbounded()||attribute.multiplicity().upperBound()>1);}
        /** Descripción: Determina si un token inicia otra feature del elemento actual. Entradas: token y conjuntos de keywords. Salida: devuelve true cuando coincide con atributo o relación. */ private static boolean isFeature(String token,Set<String> attrKeywords,Set<String> relationKeywords){return attrKeywords.contains(token)||relationKeywords.contains(token);}
        /** Descripción: Consume y devuelve el token actual. Entradas: no requiere parámetros. Salida: devuelve token o null al final. */ private Token take(){return hasMore()?tokens.get(position++):null;}
        /** Descripción: Consume un token obligatorio y registra incidencia cuando no existe. Entradas: expected: descripción esperada. Salida: devuelve token o null. */ private Token takeRequired(String expected){if(!hasMore()){state.issues.add(issue("UNEXPECTED_END_OF_DOCUMENT",document.id(),"Expected "+expected+" before end of document"));return null;}return take();}
        /** Descripción: Consulta el token actual sin consumirlo. Entradas: no requiere parámetros. Salida: devuelve token o null. */ private Token peek(){return hasMore()?tokens.get(position):null;}
        /** Descripción: Compara el token actual con un literal sin mover el cursor. Entradas: value. Salida: devuelve true si coincide. */ private boolean peekText(String value){return hasMore()&&value.equals(tokens.get(position).text());}
        /** Descripción: Obtiene el offset final del último token consumido. Entradas: no requiere parámetros. Salida: devuelve cero al inicio o el final del token anterior. */ private int previousEnd(){return position<=0?0:tokens.get(Math.min(position-1,tokens.size()-1)).end();}
    }

    /** Descripción: Tokeniza IDs, números, strings y llaves conservando offsets para source maps exactos. Entradas: text: documento textual. Salida: devuelve tokens semánticos sin espacios ni comentarios de línea. */ private static List<Token> tokenize(String text){List<Token> result=new ArrayList<>();String source=text==null?"":text;for(int i=0;i<source.length();){char c=source.charAt(i);if(Character.isWhitespace(c)){i++;continue;}if(c=='/'&&i+1<source.length()&&source.charAt(i+1)=='/'){int end=source.indexOf('\n',i+2);i=end<0?source.length():end+1;continue;}if(c=='{'||c=='}'){result.add(new Token(String.valueOf(c),i,i+1));i++;continue;}if(c=='\"'){int start=i++;StringBuilder value=new StringBuilder();boolean escape=false;while(i<source.length()){char current=source.charAt(i++);if(escape){value.append(switch(current){case 'n'->'\n';case 'r'->'\r';case 't'->'\t';default->current;});escape=false;continue;}if(current=='\\'){escape=true;continue;}if(current=='\"')break;value.append(current);}result.add(new Token(value.toString(),start,i));continue;}int start=i;while(i<source.length()&&!Character.isWhitespace(source.charAt(i))&&source.charAt(i)!='{'&&source.charAt(i)!='}')i++;result.add(new Token(source.substring(start,i),start,i));}return List.copyOf(result);}
    /** Descripción: Genera un ID de relación estable dentro del documento reconstruido sin depender del ID de transporte original. Entradas: source, relation, target y ordinal. Salida: devuelve un identificador válido y acotado. */ private static String relationId(String source,String relation,String target,int ordinal){String value=source+":"+relation+":"+target+":"+ordinal;return value.length()<=240?value:source+":"+relation+":"+ordinal;}

    private record Token(String text,int start,int end){}
    private record ValueToken(Token token,Object value){}
    private record PendingReference(String sourceId,MetaRelation relation,String targetId,TextualModelSourceDocument document,TextRange range){}
    private record ContainmentContext(String sourceId,MetaRelation relation){}
}
