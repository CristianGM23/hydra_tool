package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.Multiplicity;

/** Static, non-executing Java/Kotlin source inference used only as low-confidence fallback evidence. */
@Component
public class UpdateSiteSourceMetamodelDeriver {
    private static final Pattern JAVA_DECL=Pattern.compile("(?m)\\b(abstract\\s+class|class|interface|enum)\\s+([A-Za-z_$][\\w$]*)(?:\\s+extends\\s+([^\\{\\n]+))?(?:\\s+implements\\s+([^\\{\\n]+))?");
    private static final Pattern JAVA_FIELD=Pattern.compile("(?m)^\\s*(?:public|protected|private)\\s+(?:(?:static|final|transient|volatile)\\s+)*([A-Za-z_$][\\w$<>.?\\[\\], ]*)\\s+([A-Za-z_$][\\w$]*)\\s*(?:=|;)");
    private static final Pattern JAVA_METHOD=Pattern.compile("(?m)^\\s*(?:public|protected)\\s+(?:(?:static|final|abstract|synchronized)\\s+)*([A-Za-z_$][\\w$<>.?\\[\\], ]*|void)\\s+([A-Za-z_$][\\w$]*)\\s*\\(([^)]*)\\)");
    private static final Pattern KOTLIN_DECL=Pattern.compile("(?m)\\b((?:data\\s+|sealed\\s+|abstract\\s+)?class|interface|enum\\s+class)\\s+([A-Za-z_][\\w]*)(?:\\s*\\([^)]*\\))?(?:\\s*:\\s*([^\\{\\n]+))?");
    private static final Pattern KOTLIN_PROPERTY=Pattern.compile("(?m)\\b(?:val|var)\\s+([A-Za-z_][\\w]*)\\s*:\\s*([A-Za-z_][\\w<>.? ,]*)");
    private static final Pattern KOTLIN_FUNCTION=Pattern.compile("(?m)\\bfun\\s+([A-Za-z_][\\w]*)\\s*\\(([^)]*)\\)\\s*(?::\\s*([A-Za-z_][\\w<>.? ,]*))?");

    /** Descripción: Ejecuta la operación «derive» correspondiente a la responsabilidad de SourceUnit. Entradas: repository: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo; hintPath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ MetaModel derive(UpdateSiteRepositoryContent repository,String targetId,String hintPath) {
        List<SourceUnit> units=new ArrayList<>();Set<String> knownTypes=new LinkedHashSet<>();
        for(UpdateSiteRepositoryArtifact artifact:repository.artifacts().stream().filter(item->{UpdateSiteArtifactKind kind=UpdateSiteArtifactKinds.fromPath(item.path());return kind==UpdateSiteArtifactKind.JAVA_SOURCE||kind==UpdateSiteArtifactKind.KOTLIN_SOURCE;}).sorted(java.util.Comparator.comparing(UpdateSiteRepositoryArtifact::path)).toList()){
            String source=new String(repository.find(artifact.path()).orElse(new byte[0]),StandardCharsets.UTF_8);SourceUnit unit=parseUnit(artifact.path(),source);if(unit!=null){units.add(unit);knownTypes.add(unit.name());}
        }
        RecoveredMetamodelBuilder builder=new RecoveredMetamodelBuilder();
        for(SourceUnit unit:units){
            builder.addClass(unit.name(),unit.kind());
            unit.parents().forEach(parent->{String local=localType(parent);if(!local.isBlank()&&!local.equals(unit.name()))builder.addInheritance(unit.name(),local);});
            for(FieldSpec field:unit.fields())addField(builder,knownTypes,unit.name(),field);
            for(OperationSpec operation:unit.operations())builder.addOperation(unit.name(),operation.name(),baseType(operation.returnType()),operation.parameters());
            if(unit.kind()==MetaClassKind.INTERFACE)builder.addClass(unit.name(),MetaClassKind.INTERFACE).mergeKind(MetaClassKind.INTERFACE);
        }
        return builder.build(targetId,fileStem(hintPath),Map.of("hydra.recovery.inference","JAVA_KOTLIN_SOURCE","hydra.recovery.sourceUnits",Integer.toString(units.size())));
    }

    /** Descripción: Ejecuta la operación «parseUnit» correspondiente a la responsabilidad de SourceUnit. Entradas: path: ruta o ubicación utilizada por la operación; source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private SourceUnit parseUnit(String path,String source){
        boolean kotlin=path.toLowerCase(Locale.ROOT).endsWith(".kt");Matcher declaration=(kotlin?KOTLIN_DECL:JAVA_DECL).matcher(source);if(!declaration.find())return null;
        String token=declaration.group(1).toLowerCase(Locale.ROOT);MetaClassKind kind=token.contains("interface")?MetaClassKind.INTERFACE:token.contains("abstract")?MetaClassKind.ABSTRACT_CLASS:MetaClassKind.CLASS;String name=declaration.group(2);
        List<String> parents=new ArrayList<>();String inheritance=declaration.groupCount()>=3?declaration.group(3):null;if(inheritance!=null)for(String item:inheritance.split(","))if(!item.isBlank())parents.add(cleanParent(item));if(!kotlin&&declaration.groupCount()>=4&&declaration.group(4)!=null)for(String item:declaration.group(4).split(","))if(!item.isBlank())parents.add(cleanParent(item));
        List<FieldSpec> fields=kotlin?kotlinFields(source):javaFields(source);List<OperationSpec> operations=kotlin?kotlinOperations(source):javaOperations(source);
        return new SourceUnit(path,name,kind,List.copyOf(parents),fields,operations);
    }

    /** Descripción: Ejecuta la operación «javaFields» correspondiente a la responsabilidad de SourceUnit. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<FieldSpec> javaFields(String source){List<FieldSpec> result=new ArrayList<>();Matcher matcher=JAVA_FIELD.matcher(source);while(matcher.find()){String type=matcher.group(1).trim();String name=matcher.group(2);if(!name.equals("serialVersionUID"))result.add(new FieldSpec(name,type,many(type),optional(type)));}return List.copyOf(result);}
    /** Descripción: Ejecuta la operación «kotlinFields» correspondiente a la responsabilidad de SourceUnit. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<FieldSpec> kotlinFields(String source){List<FieldSpec> result=new ArrayList<>();Matcher matcher=KOTLIN_PROPERTY.matcher(source);while(matcher.find()){String type=matcher.group(2).trim();result.add(new FieldSpec(matcher.group(1),type,many(type),optional(type)));}return List.copyOf(result);}
    /** Descripción: Ejecuta la operación «javaOperations» correspondiente a la responsabilidad de SourceUnit. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<OperationSpec> javaOperations(String source){List<OperationSpec> result=new ArrayList<>();Matcher matcher=JAVA_METHOD.matcher(source);while(matcher.find()){String name=matcher.group(2);if(name.equals("if")||name.equals("for")||name.equals("while"))continue;result.add(new OperationSpec(name,matcher.group(1).trim(),parameters(matcher.group(3),false)));}return List.copyOf(result);}
    /** Descripción: Ejecuta la operación «kotlinOperations» correspondiente a la responsabilidad de SourceUnit. Entradas: source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<OperationSpec> kotlinOperations(String source){List<OperationSpec> result=new ArrayList<>();Matcher matcher=KOTLIN_FUNCTION.matcher(source);while(matcher.find())result.add(new OperationSpec(matcher.group(1),matcher.group(3)==null?"void":matcher.group(3).trim(),parameters(matcher.group(2),true)));return List.copyOf(result);}

    /** Descripción: Ejecuta la operación «parameters» correspondiente a la responsabilidad de SourceUnit. Entradas: source: dato de entrada necesario para la operación; kotlin: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<RecoveredMetamodelBuilder.ParameterSpec> parameters(String source,boolean kotlin){List<RecoveredMetamodelBuilder.ParameterSpec> result=new ArrayList<>();if(source==null||source.isBlank())return result;for(String raw:source.split(",")){String part=raw.trim();if(part.isBlank())continue;String name;String type;if(kotlin&&part.contains(":")){String[] pieces=part.split(":",2);name=pieces[0].replaceAll(".*\\b(val|var)\\s+","").trim();type=pieces[1].trim();}else{String[] pieces=part.replaceAll("\\bfinal\\s+","").trim().split("\\s+");if(pieces.length<2)continue;name=pieces[pieces.length-1];type=String.join(" ",java.util.Arrays.copyOf(pieces,pieces.length-1));}result.add(new RecoveredMetamodelBuilder.ParameterSpec(name,baseType(type),many(type)));}return List.copyOf(result);}
    /** Descripción: Ejecuta la operación «addField» correspondiente a la responsabilidad de SourceUnit. Entradas: builder: dato de entrada necesario para la operación; known: dato de entrada necesario para la operación; owner: dato de entrada necesario para la operación; field: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addField(RecoveredMetamodelBuilder builder,Set<String> known,String owner,FieldSpec field){String base=baseType(field.type());Multiplicity multiplicity=field.many()?Multiplicity.many():field.optional()?Multiplicity.optional():Multiplicity.one();if(known.contains(localType(base)))builder.addRelation(owner,field.name(),localType(base),multiplicity,false);else builder.addAttribute(owner,field.name(),base,multiplicity);}
    /** Descripción: Ejecuta la operación «many» correspondiente a la responsabilidad de SourceUnit. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean many(String type){String lower=type.toLowerCase(Locale.ROOT);return lower.endsWith("[]")||lower.contains("list<")||lower.contains("set<")||lower.contains("collection<")||lower.contains("iterable<")||lower.contains("sequence<")||lower.startsWith("list<")||lower.startsWith("set<");}
    /** Descripción: Ejecuta la operación «optional» correspondiente a la responsabilidad de SourceUnit. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean optional(String type){return type.contains("?")||type.toLowerCase(Locale.ROOT).contains("optional<");}
    /** Descripción: Ejecuta la operación «baseType» correspondiente a la responsabilidad de SourceUnit. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String baseType(String type){String result=type==null?"Object":type.trim().replace("?","");if(result.endsWith("[]"))result=result.substring(0,result.length()-2);int start=result.indexOf('<');int end=result.lastIndexOf('>');if(start>=0&&end>start)result=result.substring(start+1,end).split(",")[0].trim();return localType(result);}
    /** Descripción: Ejecuta la operación «cleanParent» correspondiente a la responsabilidad de SourceUnit. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String cleanParent(String value){return baseType(value.replaceAll("\\([^)]*\\)",""));}
    /** Descripción: Ejecuta la operación «localType» correspondiente a la responsabilidad de SourceUnit. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String localType(String value){String result=value==null?"":value.trim();int dot=Math.max(result.lastIndexOf('.'),result.lastIndexOf('$'));if(dot>=0)result=result.substring(dot+1);return result.replaceAll("[^A-Za-z0-9_]","");}
    /** Descripción: Ejecuta la operación «fileStem» correspondiente a la responsabilidad de SourceUnit. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String fileStem(String path){String value=path==null?"source":path.replace('\\','/');int slash=value.lastIndexOf('/');if(slash>=0)value=value.substring(slash+1);int dot=value.lastIndexOf('.');return dot>0?value.substring(0,dot):value;}

    private record SourceUnit(String path,String name,MetaClassKind kind,List<String> parents,List<FieldSpec> fields,List<OperationSpec> operations){}
    private record FieldSpec(String name,String type,boolean many,boolean optional){}
    private record OperationSpec(String name,String returnType,List<RecoveredMetamodelBuilder.ParameterSpec> parameters){}
}
