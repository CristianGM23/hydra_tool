package com.example.editor.metamodel.importing.updatesite;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.core.MetaRelationKind;

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
    private static final Pattern TS_ENUM=Pattern.compile("(?ms)^\\s*(?:export\\s+)?(?:declare\\s+)?(?:const\\s+)?enum\\s+([A-Za-z_]\\w*)\\s*\\{(.*?)\\}");
    private static final Pattern TS_DECLARATION=Pattern.compile("(?m)^\\s*(?:export\\s+)?(?:default\\s+)?(?:declare\\s+)?(interface|abstract\\s+class|class)\\s+([A-Za-z_]\\w*)(?:\\s*<[^>{]*>)?([^{]*)\\{");
    private static final Pattern TS_PROPERTY=Pattern.compile("^(?:(?:public|protected|private)\\s+)?(declare\\s+)?(?:readonly\\s+)?([A-Za-z_]\\w*)([?!])?\\s*:\\s*(.+?)\\s*$");
    private static final Pattern HYDRA_TAG=Pattern.compile("@hydra\\s+(\\w+)(?:[ \\t]+([^@*\\r\\n]+))?");
    private static final Pattern GO_NAMED_BASIC=Pattern.compile("(?m)^\\s*type\\s+([A-Za-z_]\\w*)\\s+(?:u?int(?:8|16|32|64)?|string)\\s*$");
    private static final Pattern GO_STRING_CASE=Pattern.compile("case\\s+([A-Za-z_]\\w*)\\s*:\\s*return\\s+\"([^\"]*)\"");
    private static final Pattern GO_EMBEDDED=Pattern.compile("^\\s*\\*?([A-Z]\\w*)\\s*(?://.*)?$");
    private static final Pattern GO_STRUCT_FIELD=Pattern.compile("^\\s*([A-Z]\\w*)[ \\t]+([^`/\\r\\n]+?)\\s*(?:`([^`]*)`)?\\s*(?://.*)?$");
    private static final Pattern GO_GETTER=Pattern.compile("^\\s*Get([A-Z]\\w*)\\(\\)\\s+([^\\s/]+)\\s*(?://\\s*(.*))?$");

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

    /** Descripción: Recupera un metamodelo de código TypeScript sin ejecutarlo: enumeraciones (con el literal exacto de la etiqueta JSDoc «@hydra literal» si existe), clases, clases abstractas e interfaces, «extends» como herencia e «implements» como implementación, propiedades con su multiplicidad («?» opcional, arrays múltiples, «@hydra multiplicity» para lo que TypeScript no expresa) y el tipo de relación («@hydra containment|aggregation»). Las propiedades «declare» son los miembros de una interfaz implementada y no se duplican. Entradas: source: código; targetId: id pedido; path: fichero. Salida: el metamodelo recuperado. */ private MetaModel deriveTypeScript(String source,String targetId,String path){
        RecoveredMetamodelBuilder builder=new RecoveredMetamodelBuilder();
        Matcher enums=TS_ENUM.matcher(source);
        while(enums.find()){
            List<String> literals=new ArrayList<>();
            for(Segment member:segments(enums.group(2),',')){
                String name=member.code().replaceAll("=.*$","").trim();
                if(name.matches("[A-Za-z_$][\\w$]*"))literals.add(member.tags().getOrDefault("literal",name));
            }
            builder.addEnum(enums.group(1),literals);
        }
        Map<String,String> kinds=new LinkedHashMap<>();Map<String,String> heritage=new LinkedHashMap<>();Map<String,Map<String,String>> classTags=new LinkedHashMap<>();
        Matcher declaration=TS_DECLARATION.matcher(source);
        while(declaration.find()){
            String name=declaration.group(2);kinds.put(name,declaration.group(1));heritage.put(name,declaration.group(3)==null?"":declaration.group(3));
            classTags.put(name,tags(precedingDocComment(source,declaration.start())));
            builder.addClass(name,kind(declaration.group(1))).mergeKind(kind(declaration.group(1)));
        }
        for(Map.Entry<String,String> entry:heritage.entrySet()){
            String name=entry.getKey(),clauses=entry.getValue();
            Matcher extendsClause=Pattern.compile("\\bextends\\s+(.+?)(?=\\bimplements\\b|$)").matcher(clauses);
            if(extendsClause.find())for(String parent:extendsClause.group(1).split(","))addSuper(builder,name,baseType(parent),kinds,false);
            Matcher implementsClause=Pattern.compile("\\bimplements\\s+(.+)$").matcher(clauses);
            if(implementsClause.find())for(String parent:implementsClause.group(1).split(","))addSuper(builder,name,baseType(parent),kinds,true);
            String more=classTags.get(name).get("extends");
            if(more!=null)for(String parent:more.split(","))addSuper(builder,name,baseType(parent),kinds,false);
        }
        for(TypeBody body:typeBodies(source,TS_DECLARATION,2)){
            for(Segment member:segments(body.body(),';')){
                Matcher field=TS_PROPERTY.matcher(member.code());
                if(!field.matches()||field.group(1)!=null)continue; // `declare`: a member of an implemented interface, declared there
                String type=field.group(4).replaceAll("\\s*=.*$","").trim();boolean optional="?".equals(field.group(3)); // an initializer is not part of the type
                if(type.matches(".*\\|\\s*(?:undefined|null)\\s*$")){optional=true;type=type.replaceAll("\\|\\s*(?:undefined|null)\\s*$","").trim();}
                boolean many=type.endsWith("[]")||type.startsWith("Array<")||type.startsWith("ReadonlyArray<");
                record(builder,body.name(),field.group(2),baseType(type),many,optional,member.tags(),kinds.keySet());
            }
        }
        builder.adaptStructuralInterfaces("typescript");
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

    /** Descripción: Recupera un metamodelo de código Go sin ejecutarlo: structs (abstractos si su comentario de documentación lo dice), interfaces, enumeraciones «type X int» con sus constantes (literal exacto del método String() si existe), struct incrustado como herencia e interfaz incrustada en un struct como implementación, interfaces incrustadas como herencia entre interfaces, campos con el nombre y la opcionalidad de su etiqueta json («omitempty»), getters de interfaz como propiedades y la etiqueta «hydra» para el tipo de relación y las multiplicidades que Go no expresa. Entradas: source: código; targetId: id pedido; path: fichero. Salida: el metamodelo recuperado. */ private MetaModel deriveGo(String source,String targetId,String path){
        RecoveredMetamodelBuilder builder=new RecoveredMetamodelBuilder();
        Map<String,String> kinds=new LinkedHashMap<>();
        Matcher structs=GO_STRUCT.matcher(source);
        while(structs.find()){
            String name=structs.group(1);boolean abstractType=precedingLineComments(source,structs.start()).toLowerCase(Locale.ROOT).contains("abstract");
            kinds.put(name,abstractType?"abstract class":"class");builder.addClass(name,abstractType?MetaClassKind.ABSTRACT_CLASS:MetaClassKind.CLASS).mergeKind(abstractType?MetaClassKind.ABSTRACT_CLASS:MetaClassKind.CLASS);
        }
        Matcher interfaces=GO_INTERFACE.matcher(source);
        while(interfaces.find()){kinds.put(interfaces.group(1),"interface");builder.addClass(interfaces.group(1),MetaClassKind.INTERFACE).mergeKind(MetaClassKind.INTERFACE);}
        Matcher named=GO_NAMED_BASIC.matcher(source);
        while(named.find()){
            String name=named.group(1);List<String> literals=new ArrayList<>();
            Matcher stringer=Pattern.compile("(?s)func\\s*\\(\\s*\\w+\\s+"+Pattern.quote(name)+"\\s*\\)\\s*String\\(\\)\\s*string\\s*\\{(.*?)\\n}").matcher(source);
            if(stringer.find()){Matcher cases=GO_STRING_CASE.matcher(stringer.group(1));while(cases.find())literals.add(cases.group(2));}
            if(literals.isEmpty())literals.addAll(goConstants(source,name));
            if(!literals.isEmpty()&&!kinds.containsKey(name))builder.addEnum(name,literals);
        }
        for(TypeBody body:typeBodies(source,GO_STRUCT,1)){
            for(String line:body.body().split("\\R")){
                Matcher embedded=GO_EMBEDDED.matcher(line);
                if(embedded.matches()){addSuper(builder,body.name(),embedded.group(1),kinds,"interface".equals(kinds.get(embedded.group(1))));continue;}
                Matcher field=GO_STRUCT_FIELD.matcher(line);
                if(!field.matches())continue;
                goMember(builder,body.name(),field.group(1),field.group(2),field.group(3),kinds.keySet());
            }
        }
        for(TypeBody body:typeBodies(source,GO_INTERFACE,1)){
            for(String line:body.body().split("\\R")){
                Matcher embedded=GO_EMBEDDED.matcher(line);
                if(embedded.matches()){addSuper(builder,body.name(),embedded.group(1),kinds,false);continue;}
                Matcher getter=GO_GETTER.matcher(line);
                if(getter.matches())goMember(builder,body.name(),getter.group(1),getter.group(2),getter.group(3),kinds.keySet());
            }
        }
        builder.adaptStructuralInterfaces("go");
        return builder.build(targetId,fileStem(path),Map.of("hydra.recovery.inference","GO_SOURCE","hydra.recovery.nonExecuting","true"));
    }

    /** Descripción: Registra un campo o getter Go: nombre de la etiqueta json (o el del campo), «omitempty» como opcional, «[]» como múltiple y la etiqueta «hydra». */
    private void goMember(RecoveredMetamodelBuilder builder,String owner,String goName,String rawType,String tag,Set<String> known){
        String type=rawType.trim();Map<String,String> tags=goTags(tag==null?"":tag);
        String json=tags.getOrDefault("json","");String name=json.isBlank()||json.startsWith(",")?lowerFirst(goName):json.split(",")[0];
        boolean optional=json.contains(",omitempty");boolean many=type.startsWith("[]");
        String base=type.replaceFirst("^\\[\\]","").replaceFirst("^\\*","");
        Map<String,String> hydra=new LinkedHashMap<>();
        for(String item:tags.getOrDefault("hydra","").split(",")){String value=item.trim();if(value.isEmpty())continue;int eq=value.indexOf('=');if(eq>0)hydra.put(value.substring(0,eq),value.substring(eq+1));else hydra.put(value,"");}
        record(builder,owner,name,base,many,optional,hydra,known);
    }

    /** Descripción: Añade a «owner» la propiedad «name» de tipo «base»: relación si el tipo es una clase recuperada, atributo en otro caso; con la multiplicidad expresada en el código o en la etiqueta «multiplicity». */
    private void record(RecoveredMetamodelBuilder builder,String owner,String name,String base,boolean many,boolean optional,Map<String,String> tags,Set<String> known){
        int lower=optional?0:1;Integer upper=many?null:1;
        String declared=tags.get("multiplicity");
        if(declared!=null&&declared.matches("\\d+\\.\\.(?:\\d+|\\*)")){String[] bounds=declared.split("\\.\\.");lower=Integer.parseInt(bounds[0]);upper=bounds[1].equals("*")?null:Integer.valueOf(bounds[1]);}
        Multiplicity multiplicity=new Multiplicity(lower,upper);
        if(known.contains(base)){
            MetaRelationKind kind=tags.containsKey("containment")?MetaRelationKind.CONTAINMENT:tags.containsKey("aggregation")?MetaRelationKind.AGGREGATION:MetaRelationKind.REFERENCE;
            builder.addRelation(owner,name,base,multiplicity,kind);
        } else builder.addAttribute(owner,name,builder.hasEnum(base)?base:mapPortableType(base),multiplicity);
    }

    /** Descripción: Añade herencia o implementación según la declaración (en TypeScript «implements»; en Go, interfaz incrustada en un struct). */
    private void addSuper(RecoveredMetamodelBuilder builder,String name,String parent,Map<String,String> kinds,boolean implementation){
        if(parent==null||parent.isBlank()||parent.equals(name))return;
        if(implementation)builder.addImplementation(name,parent);else builder.addInheritance(name,parent);
    }

    /** Descripción: Divide un cuerpo en miembros separados por «separator» o por fin de línea, fuera de llaves, paréntesis y cadenas; cada miembro conserva las etiquetas «@hydra» de los comentarios que lo preceden. */
    private List<Segment> segments(String body,char separator){
        List<Segment> out=new ArrayList<>();StringBuilder code=new StringBuilder();StringBuilder comments=new StringBuilder();
        int depth=0;char quote=0;
        for(int i=0;i<body.length();i++){
            char c=body.charAt(i),next=i+1<body.length()?body.charAt(i+1):'\0';
            if(quote!=0){code.append(c);if(c=='\\'&&i+1<body.length()){code.append(next);i++;}else if(c==quote)quote=0;continue;}
            if(c=='/'&&next=='*'){int end=body.indexOf("*/",i+2);end=end<0?body.length():end+2;comments.append(body,i,end).append(' ');i=end-1;continue;}
            if(c=='/'&&next=='/'){int end=body.indexOf('\n',i);end=end<0?body.length():end;comments.append(body,i,end).append(' ');i=end-1;continue;}
            if(c=='"'||c=='\''||c=='`'){quote=c;code.append(c);continue;}
            if(c=='{'||c=='('||c=='[')depth++;else if((c=='}'||c==')'||c==']')&&depth>0)depth--;
            if(depth==0&&(c==separator||c=='\n'||c=='\r')){flush(out,code,comments);continue;}
            if(depth==0)code.append(c);
            else if(c!='\n'&&c!='\r')code.append(c);
        }
        flush(out,code,comments);
        return out;
    }

    /** Descripción: Cierra el miembro en curso; un comentario sin código queda pendiente para el miembro siguiente. */
    private void flush(List<Segment> out,StringBuilder code,StringBuilder comments){
        String text=code.toString().trim();
        if(text.isEmpty())return;
        out.add(new Segment(text,tags(comments.toString())));code.setLength(0);comments.setLength(0);
    }

    /** Descripción: Etiquetas «@hydra nombre [valor]» de un comentario. */
    private Map<String,String> tags(String comment){
        Map<String,String> out=new LinkedHashMap<>();Matcher tag=HYDRA_TAG.matcher(comment==null?"":comment);
        while(tag.find())out.put(tag.group(1),tag.group(2)==null?"":tag.group(2).trim());
        return out;
    }

    /** Descripción: Comentario de documentación /** ... *&#47; inmediatamente anterior a una declaración (vacío si no lo hay). */
    private String precedingDocComment(String source,int start){
        String before=source.substring(Math.max(0,start-2_000),start);
        Matcher comment=Pattern.compile("(?s)/\\*\\*((?:(?!\\*/).)*)\\*/\\s*$").matcher(before);
        return comment.find()?comment.group(1):"";
    }

    /** Descripción: Líneas de comentario «//» inmediatamente anteriores a una declaración Go. */
    private String precedingLineComments(String source,int start){
        String[] lines=source.substring(Math.max(0,start-2_000),start).split("\\R",-1);
        StringBuilder out=new StringBuilder();
        for(int i=lines.length-2;i>=0&&lines[i].trim().startsWith("//");i--)out.insert(0,lines[i]).append('\n');
        return out.toString();
    }

    /** Descripción: Etiquetas de un campo Go: clave:"valor" ... */
    private Map<String,String> goTags(String tag){
        Map<String,String> out=new LinkedHashMap<>();Matcher pair=Pattern.compile("(\\w+):\"([^\"]*)\"").matcher(tag);
        while(pair.find())out.put(pair.group(1),pair.group(2));
        return out;
    }
    /** Descripción: Constantes de una enumeración Go en sus bloques const: «Nombre Tipo = valor» y las líneas siguientes que solo nombran una constante (iota implícito) mientras sigan siendo de ese tipo; el prefijo del tipo se quita del nombre. */
    private List<String> goConstants(String source,String type){
        List<String> out=new ArrayList<>();
        Matcher block=Pattern.compile("(?s)\\bconst\\s*\\((.*?)\\)").matcher(source);
        while(block.find()){
            String current=null;
            for(String line:block.group(1).split("\\R")){
                String code=line.replaceAll("//.*$","").trim();
                if(code.isEmpty())continue;
                Matcher typed=Pattern.compile("^([A-Za-z_]\\w*)\\s+([A-Za-z_]\\w*)\\s*=").matcher(code);
                String constant=null;
                if(typed.find()){current=typed.group(2);constant=typed.group(1);}
                else if(code.matches("[A-Za-z_]\\w*"))constant=code;
                else current=null;
                if(constant!=null&&type.equals(current))out.add(constant.startsWith(type)&&constant.length()>type.length()?constant.substring(type.length()):constant);
            }
        }
        return out;
    }

    private record Segment(String code,Map<String,String> tags){}
    /** Descripción: Ejecuta la operación «typeBodies» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: source: dato de entrada necesario para la operación; declaration: dato de entrada necesario para la operación; nameGroup: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<TypeBody> typeBodies(String source,Pattern declaration,int nameGroup){List<TypeBody> result=new ArrayList<>();Matcher matcher=declaration.matcher(source);while(matcher.find()){String name=matcher.group(nameGroup);int open=source.indexOf('{',matcher.start());if(open<0)continue;int close=matchingBrace(source,open);if(close>open)result.add(new TypeBody(name,source.substring(open+1,close)));}return List.copyOf(result);}
    /** Descripción: Ejecuta la operación «matchingBrace» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: source: dato de entrada necesario para la operación; open: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int matchingBrace(String source,int open){int depth=0;for(int i=open;i<source.length();i++){char c=source.charAt(i);if(c=='{')depth++;else if(c=='}'&&--depth==0)return i;}return -1;}
    /** Descripción: Ejecuta la operación «addParents» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: builder: dato de entrada necesario para la operación; name: dato de entrada necesario para la operación; raw: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addParents(RecoveredMetamodelBuilder builder,String name,String raw){if(raw==null||raw.isBlank())return;for(String parent:raw.split(",")){String clean=baseType(parent.trim());if(!clean.isBlank()&&!clean.equals(name))builder.addInheritance(name,clean);}}
    /** Descripción: Ejecuta la operación «kind» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: token: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClassKind kind(String token){String lower=token.toLowerCase(Locale.ROOT);return lower.contains("interface")?MetaClassKind.INTERFACE:lower.contains("abstract")?MetaClassKind.ABSTRACT_CLASS:MetaClassKind.CLASS;}
    /** Descripción: Ejecuta la operación «baseType» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String baseType(String type){String result=type.trim().replace("?","");if(result.endsWith("[]"))result=result.substring(0,result.length()-2);int start=result.indexOf('<'),end=result.lastIndexOf('>');if(start>=0&&end>start)result=result.substring(start+1,end).split(",")[0].trim();return result.replaceAll("[^A-Za-z0-9_]","");}
    /** Descripción: Ejecuta la operación «mapPortableType» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String mapPortableType(String type){return switch(type.toLowerCase(Locale.ROOT)){case "string"->"String";case "boolean","bool"->"Boolean";case "number","float64"->"Double";case "float32"->"Float";case "int8"->"Byte";case "int16"->"Short";case "int","int32","integer"->"Integer";case "int64","uint64","long"->"Long";case "date","time","time_time","time.time"->"Date";case "byte","uint8"->"Byte";default->type;};}
    /** Descripción: Ejecuta la operación «fileStem» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String fileStem(String path){String value=path==null?"source":path.replace('\\','/');int slash=value.lastIndexOf('/');if(slash>=0)value=value.substring(slash+1);int dot=value.lastIndexOf('.');return dot>0?value.substring(0,dot):value;}
    /** Descripción: Ejecuta la operación «lowerFirst» correspondiente a la responsabilidad de UpdateSitePortableSourceMetamodelDeriver. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String lowerFirst(String value){return value.isBlank()?value:Character.toLowerCase(value.charAt(0))+value.substring(1);}
    private record TypeBody(String name,String body){}
}
