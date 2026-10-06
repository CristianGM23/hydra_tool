package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

/**
 * Static, non-executing recovery for portable source formats commonly found in
 * Git repositories but not represented by a compiled Eclipse update site.
 */
@Component
public class UpdateSitePortableSourceMetamodelDeriver {
    private static final Pattern XCORE_TYPE=Pattern.compile("(?m)^\\s*(abstract\\s+class|class|interface)\\s+([A-Za-z_][\\w]*)\\s*(?:extends\\s+([^\\{]+))?");
    private static final Pattern XCORE_ENUM=Pattern.compile("(?ms)^\\s*enum\\s+([A-Za-z_][\\w]*)\\s*\\{([^}]*)}");
    private static final Pattern XCORE_FEATURE=Pattern.compile("(?m)^\\s*(attr|ref|contains)\\s+([A-Za-z_][\\w]*(?:\\[\\*?\\])?)\\s+([A-Za-z_][\\w]*)");
    private static final Pattern TS_TYPE=Pattern.compile("(?m)^\\s*(?:export\\s+)?(interface|abstract\\s+class|class)\\s+([A-Za-z_][\\w]*)\\s*(?:extends\\s+([^\\{]+))?\\s*\\{");
    private static final Pattern TS_FIELD=Pattern.compile("(?m)(?:^|;)\\s*(?:readonly\\s+)?([A-Za-z_][\\w]*)\\??\\s*:\\s*([^;\\n}]+)");
    private static final Pattern JS_CLASS=Pattern.compile("(?m)^\\s*(?:export\\s+default\\s+|export\\s+)?class\\s+([A-Za-z_][\\w]*)\\s*(?:extends\\s+([A-Za-z_][\\w]*))?\\s*\\{");
    private static final Pattern JS_FIELD=Pattern.compile("(?m)^\\s*([A-Za-z_][\\w]*)\\s*=\\s*([^;\\n]+);?");
    private static final Pattern JS_THIS_FIELD=Pattern.compile("(?m)this\\.([A-Za-z_][\\w]*)\\s*=\\s*([^;\\n]+);?");
    private static final Pattern JS_METHOD=Pattern.compile("(?m)(?:(?:static|async|get|set)\\s+)*([A-Za-z_$][\\w$]*)\\s*\\(([^)]*)\\)\\s*\\{");
    private static final Pattern GO_STRUCT=Pattern.compile("(?m)^\\s*type\\s+([A-Za-z_][\\w]*)\\s+struct\\s*\\{");
    private static final Pattern GO_INTERFACE=Pattern.compile("(?m)^\\s*type\\s+([A-Za-z_][\\w]*)\\s+interface\\s*\\{");
    private static final Pattern GO_FIELD=Pattern.compile("(?m)^\\s*([A-Z][A-Za-z0-9_]*)\\s+([^`\\n]+)");

    /** Descripción: Ejecuta la operación «derive» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: kind: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse; targetId: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetaModel derive(UpdateSiteArtifactKind kind, byte[] content, String targetId, String path) {
        String source=new String(content,StandardCharsets.UTF_8);
        return switch(kind) {
            case XCORE -> deriveXcore(source,targetId,path);
            case TYPESCRIPT_SOURCE -> deriveTypeScript(source,targetId,path);
            case JAVASCRIPT_SOURCE -> deriveJavaScript(source,targetId,path);
            case GO_SOURCE -> deriveGo(source,targetId,path);
            default -> throw new IllegalArgumentException("Unsupported portable source recovery: "+kind);
        };
    }

    /** Descripción: Ejecuta la operación «deriveXcore» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: source: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel deriveXcore(String source,String targetId,String path){
        RecoveredMetamodelBuilder builder=new RecoveredMetamodelBuilder();
        Matcher types=XCORE_TYPE.matcher(source);Set<String> known=new LinkedHashSet<>();
        while(types.find()) {String name=types.group(2);known.add(name);builder.addClass(name,kind(types.group(1)));addParents(builder,name,types.group(3));}
        Matcher enums=XCORE_ENUM.matcher(source);while(enums.find()){List<String> literals=List.of(enums.group(2).split("[,\\s]+" )).stream().filter(v->!v.isBlank()).toList();builder.addEnum(enums.group(1),literals);}
        for(TypeBody body:typeBodies(source,XCORE_TYPE,2)) {Matcher feature=XCORE_FEATURE.matcher(body.body());while(feature.find()){String rawType=feature.group(2);String name=feature.group(3);boolean many=rawType.contains("[")&&rawType.contains("*");String type=rawType.replaceAll("\\[.*?]","");Multiplicity multiplicity=many?Multiplicity.many():Multiplicity.optional();if(feature.group(1).equals("ref")||feature.group(1).equals("contains")||known.contains(type))builder.addRelation(body.name(),name,type,multiplicity,feature.group(1).equals("contains"));else builder.addAttribute(body.name(),name,type,multiplicity);}}
        return builder.build(targetId,fileStem(path),Map.of("hydra.recovery.inference","XCORE_STATIC","hydra.recovery.nonExecuting","true"));
    }

    /** Descripción: Ejecuta la operación «deriveTypeScript» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: source: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel deriveTypeScript(String source,String targetId,String path){
        RecoveredMetamodelBuilder builder=new RecoveredMetamodelBuilder();Set<String> known=new LinkedHashSet<>();Matcher declaration=TS_TYPE.matcher(source);
        while(declaration.find()){known.add(declaration.group(2));builder.addClass(declaration.group(2),kind(declaration.group(1)));addParents(builder,declaration.group(2),declaration.group(3));}
        for(TypeBody body:typeBodies(source,TS_TYPE,2)){Matcher field=TS_FIELD.matcher(body.body());while(field.find()){String type=field.group(2).trim();boolean many=type.endsWith("[]")||type.startsWith("Array<")||type.startsWith("ReadonlyArray<");String base=baseType(type);Multiplicity multiplicity=many?Multiplicity.many():Multiplicity.optional();if(known.contains(base))builder.addRelation(body.name(),field.group(1),base,multiplicity,false);else builder.addAttribute(body.name(),field.group(1),mapPortableType(base),multiplicity);}}
        return builder.build(targetId,fileStem(path),Map.of("hydra.recovery.inference","TYPESCRIPT_SOURCE","hydra.recovery.nonExecuting","true"));
    }


    /** Descripción: Ejecuta la operación «deriveJavaScript» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: source: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel deriveJavaScript(String source,String targetId,String path){
        RecoveredMetamodelBuilder builder=new RecoveredMetamodelBuilder();Matcher declaration=JS_CLASS.matcher(source);
        while(declaration.find()){String name=declaration.group(1);builder.addClass(name,MetaClassKind.CLASS);if(declaration.group(2)!=null)builder.addInheritance(name,declaration.group(2));}
        for(TypeBody body:typeBodies(source,JS_CLASS,1)){
            Set<String> fields=new LinkedHashSet<>();Matcher field=JS_FIELD.matcher(body.body());while(field.find()){if(fields.add(field.group(1)))builder.addAttribute(body.name(),field.group(1),inferJavaScriptType(field.group(2)),Multiplicity.optional());}
            Matcher assigned=JS_THIS_FIELD.matcher(body.body());while(assigned.find()){if(fields.add(assigned.group(1)))builder.addAttribute(body.name(),assigned.group(1),inferJavaScriptType(assigned.group(2)),Multiplicity.optional());}
            Matcher method=JS_METHOD.matcher(body.body());while(method.find()){if(!isTopLevelJavaScriptPosition(body.body(),method.start())||method.group(1).equals("constructor"))continue;List<RecoveredMetamodelBuilder.ParameterSpec> parameters=java.util.Arrays.stream(method.group(2).split(",")).map(String::trim).filter(value->!value.isBlank()).map(value->new RecoveredMetamodelBuilder.ParameterSpec(value.replaceAll("=.*$","").trim(),"Object",false)).toList();builder.addOperation(body.name(),method.group(1),"Object",parameters);}
        }
        return builder.build(targetId,fileStem(path),Map.of("hydra.recovery.inference","JAVASCRIPT_SOURCE","hydra.recovery.nonExecuting","true"));
    }


    /** Returns true when an offset is at class-body level, ignoring braces in strings and comments. */
    /** Descripción: Evalúa la condición «isTopLevelJavaScriptPosition» sin modificar el estado externo. Entradas: source: dato de entrada necesario para la operación; offset: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isTopLevelJavaScriptPosition(String source,int offset){
        int depth=0;char quote=0;boolean escaped=false,lineComment=false,blockComment=false;
        for(int i=0;i<offset;i++){char c=source.charAt(i),next=i+1<offset?source.charAt(i+1):'\0';
            if(lineComment){if(c=='\n'||c=='\r')lineComment=false;continue;}
            if(blockComment){if(c=='*'&&next=='/'){blockComment=false;i++;}continue;}
            if(quote!=0){if(escaped){escaped=false;continue;}if(c=='\\'){escaped=true;continue;}if(c==quote)quote=0;continue;}
            if(c=='/'&&next=='/'){lineComment=true;i++;continue;}if(c=='/'&&next=='*'){blockComment=true;i++;continue;}
            if(c=='\''||c=='"'||c=='`'){quote=c;continue;}if(c=='{')depth++;else if(c=='}'&&depth>0)depth--;
        }
        return depth==0;
    }

    /** Descripción: Ejecuta la operación «inferJavaScriptType» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: expression: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String inferJavaScriptType(String expression){String value=expression==null?"":expression.trim();if(value.matches("^[\"'].*[\"']$"))return "String";if(value.matches("^(true|false)$"))return "Boolean";if(value.matches("^-?\\d+$"))return "Integer";if(value.matches("^-?\\d+\\.\\d+$"))return "Double";if(value.startsWith("new Date("))return "Date";if(value.startsWith("[")&&value.endsWith("]"))return "Object";return "Object";}

    /** Descripción: Ejecuta la operación «deriveGo» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: source: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel deriveGo(String source,String targetId,String path){
        RecoveredMetamodelBuilder builder=new RecoveredMetamodelBuilder();Set<String> known=new LinkedHashSet<>();Matcher structs=GO_STRUCT.matcher(source);while(structs.find()){known.add(structs.group(1));builder.addClass(structs.group(1),MetaClassKind.CLASS);}Matcher interfaces=GO_INTERFACE.matcher(source);while(interfaces.find()){known.add(interfaces.group(1));builder.addClass(interfaces.group(1),MetaClassKind.INTERFACE).mergeKind(MetaClassKind.INTERFACE);}
        for(TypeBody body:typeBodies(source,GO_STRUCT,1)){Matcher field=GO_FIELD.matcher(body.body());while(field.find()){String type=field.group(2).trim().split("\\s+`",2)[0].trim();boolean many=type.startsWith("[]");String base=type.replaceFirst("^\\[\\]","").replaceFirst("^\\*","");Multiplicity multiplicity=many?Multiplicity.many():Multiplicity.optional();if(known.contains(base))builder.addRelation(body.name(),lowerFirst(field.group(1)),base,multiplicity,false);else builder.addAttribute(body.name(),lowerFirst(field.group(1)),mapPortableType(base),multiplicity);}}
        return builder.build(targetId,fileStem(path),Map.of("hydra.recovery.inference","GO_SOURCE","hydra.recovery.nonExecuting","true"));
    }

    /** Descripción: Ejecuta la operación «typeBodies» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: source: dato de entrada necesario para la operación; declaration: dato de entrada necesario para la operación; nameGroup: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<TypeBody> typeBodies(String source,Pattern declaration,int nameGroup){List<TypeBody> result=new ArrayList<>();Matcher matcher=declaration.matcher(source);while(matcher.find()){String name=matcher.group(nameGroup);int open=source.indexOf('{',matcher.start());if(open<0)continue;int close=matchingBrace(source,open);if(close>open)result.add(new TypeBody(name,source.substring(open+1,close)));}return List.copyOf(result);}
    /** Descripción: Ejecuta la operación «matchingBrace» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: source: dato de entrada necesario para la operación; open: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int matchingBrace(String source,int open){int depth=0;for(int i=open;i<source.length();i++){char c=source.charAt(i);if(c=='{')depth++;else if(c=='}'&&--depth==0)return i;}return -1;}
    /** Descripción: Ejecuta la operación «addParents» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: builder: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación; raw: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addParents(RecoveredMetamodelBuilder builder,String name,String raw){if(raw==null||raw.isBlank())return;for(String parent:raw.split(",")){String clean=baseType(parent.trim());if(!clean.isBlank()&&!clean.equals(name))builder.addInheritance(name,clean);}}
    /** Descripción: Ejecuta la operación «kind» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: token: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClassKind kind(String token){String lower=token.toLowerCase(Locale.ROOT);return lower.contains("interface")?MetaClassKind.INTERFACE:lower.contains("abstract")?MetaClassKind.ABSTRACT_CLASS:MetaClassKind.CLASS;}
    /** Descripción: Ejecuta la operación «baseType» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String baseType(String type){String result=type.trim().replace("?","");if(result.endsWith("[]"))result=result.substring(0,result.length()-2);int start=result.indexOf('<'),end=result.lastIndexOf('>');if(start>=0&&end>start)result=result.substring(start+1,end).split(",")[0].trim();return result.replaceAll("[^A-Za-z0-9_]","");}
    /** Descripción: Ejecuta la operación «mapPortableType» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String mapPortableType(String type){return switch(type.toLowerCase(Locale.ROOT)){case "string"->"String";case "boolean","bool"->"Boolean";case "number","float32","float64"->"Double";case "int","int8","int16","int32","integer"->"Integer";case "int64","uint64","long"->"Long";case "date","time","time_time"->"Date";case "byte","uint8"->"Byte";default->type;};}
    /** Descripción: Ejecuta la operación «fileStem» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String fileStem(String path){String value=path==null?"source":path.replace('\\','/');int slash=value.lastIndexOf('/');if(slash>=0)value=value.substring(slash+1);int dot=value.lastIndexOf('.');return dot>0?value.substring(0,dot):value;}
    /** Descripción: Ejecuta la operación «lowerFirst» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String lowerFirst(String value){return value.isBlank()?value:Character.toLowerCase(value.charAt(0))+value.substring(1);}
    private record TypeBody(String name,String body){}
}
