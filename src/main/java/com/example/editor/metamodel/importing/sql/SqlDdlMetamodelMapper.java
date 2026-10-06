package com.example.editor.metamodel.importing.sql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.importing.support.ImportParsingSupport;
import com.example.editor.metamodel.importing.sql.SqlDdlAst.Column;
import com.example.editor.metamodel.importing.sql.SqlDdlAst.ForeignKey;
import com.example.editor.metamodel.importing.sql.SqlDdlAst.Table;

/** Maps the SQL adapter AST into the only canonical hub used by HYDRA: MetaModel. */
public final class SqlDdlMetamodelMapper {
    /** Descripción: Ejecuta la operación «map» correspondiente a la responsabilidad de SqlDdlMetamodelMapper. Entradas: document: dato de entrada necesario para la operación; sourceName: dato de entrada necesario para la operación; requestedId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ public MetaModel map(SqlDdlAst.Document document,String sourceName,String requestedId) {
        String base=ImportParsingSupport.fileBaseName(sourceName); String modelId=ImportParsingSupport.safeId(requestedId==null||requestedId.isBlank()?base:requestedId,"sql-schema");
        Map<String,String> tableIds=new LinkedHashMap<>(); for(Table table:document.tables())tableIds.put(key(table.qualifiedName()),"class:"+ImportParsingSupport.safeId(table.qualifiedName(),"table"));
        Map<String,String> tableAliases=unambiguousAliases(tableIds);
        Map<String,String> enumIds=new LinkedHashMap<>(); for(var value:document.enums())enumIds.put(key(value.qualifiedName()),"enum:"+ImportParsingSupport.safeId(value.qualifiedName(),"enum"));
        Map<String,String> enumAliases=unambiguousAliases(enumIds);
        List<MetaEnum> enums=new ArrayList<>();
        for(var value:document.enums()) { String enumId=enumIds.get(key(value.qualifiedName())); List<MetaEnumLiteral> literals=new ArrayList<>(); int i=0; for(String literal:value.literals())literals.add(new MetaEnumLiteral(ImportParsingSupport.literalId(enumId,literal),literal,i++,Map.of("sql.literal",literal))); enums.add(new MetaEnum(enumId,simple(value.qualifiedName()),literals,Map.of("sql.qualifiedName",value.qualifiedName()))); }
        List<MetaClass> classes=new ArrayList<>();
        for(Table table:document.tables()) { String classId=tableIds.get(key(table.qualifiedName())); List<MetaAttribute> attributes=new ArrayList<>(); for(Column column:table.columns()) attributes.add(attribute(classId,column,enumIds,enumAliases)); Map<String,String> annotations=new LinkedHashMap<>(table.annotations()); annotations.put("sql.table",table.qualifiedName()); annotations.put("sql.primaryKeys",joinKeys(table.primaryKeys().stream().flatMap(value->value.columns().stream()).toList())); annotations.put("sql.uniqueKeys",joinKeys(table.uniqueKeys().stream().flatMap(value->value.columns().stream()).toList())); classes.add(new MetaClass(classId,ImportParsingSupport.pascal(simple(table.qualifiedName()),"Table"),MetaClassKind.CLASS,attributes,List.of(),annotations)); }
        List<MetaRelation> relations=new ArrayList<>();
        for(Table table:document.tables()) { String sourceId=tableIds.get(key(table.qualifiedName())); Map<String,Column> columns=indexColumns(table); for(ForeignKey fk:table.foreignKeys()) { String targetId=tableIds.get(key(fk.targetTable())); if(targetId==null)targetId=tableAliases.get(key(simple(fk.targetTable()))); if(targetId==null)continue; String relationName=fk.name().isBlank()?"fk_"+String.join("_",fk.columns()):fk.name(); boolean required=fk.columns().stream().map(name->columns.get(key(name))).filter(java.util.Objects::nonNull).allMatch(column->!column.nullable()); Map<String,String> annotations=new LinkedHashMap<>(); annotations.put("sql.foreignKey",relationName); annotations.put("sql.columns",String.join(",",fk.columns())); annotations.put("sql.targetColumns",String.join(",",fk.targetColumns())); if(!fk.onDelete().isBlank())annotations.put("sql.onDelete",fk.onDelete()); if(!fk.onUpdate().isBlank())annotations.put("sql.onUpdate",fk.onUpdate()); relations.add(new MetaRelation(ImportParsingSupport.relationId(sourceId,relationName),ImportParsingSupport.camel(relationName,"reference"),sourceId,targetId,MetaRelationKind.REFERENCE,Multiplicity.many(),required?Multiplicity.one():Multiplicity.optional(),"",true,true,annotations)); } }
        Map<String,String> annotations=new LinkedHashMap<>(); annotations.put("sourceFormat","sql-ddl"); annotations.put("sql.dialect",document.dialect().name()); annotations.put("sql.sourceName",sourceName==null?"":sourceName);
        return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION,modelId,ImportParsingSupport.pascal(base,"SqlSchema"),classes,enums,List.of(),relations,annotations);
    }

    /** Descripción: Ejecuta la operación «attribute» correspondiente a la responsabilidad de SqlDdlMetamodelMapper. Entradas: classId: identificador que delimita el recurso objetivo; column: dato de entrada necesario para la operación; enumIds: dato de entrada necesario para la operación; enumAliases: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaAttribute attribute(String classId,Column column,Map<String,String> enumIds,Map<String,String> enumAliases) {
        Map<String,String> annotations=new LinkedHashMap<>(column.annotations()); annotations.put("sql.nullable",Boolean.toString(column.nullable()));
        if(column.primaryKey())annotations.put("sql.primaryKey","true"); if(column.unique())annotations.put("sql.unique","true"); if(column.generated())annotations.put("sql.generated","true");
        MetaTypeReference type=sqlType(column.sqlType(),enumIds,enumAliases); Multiplicity multiplicity=column.nullable()?Multiplicity.optional():Multiplicity.one();
        return new MetaAttribute(ImportParsingSupport.attributeId(classId,column.name()),ImportParsingSupport.camel(column.name(),"value"),type,multiplicity,true,true,column.defaultExpression(),annotations);
    }
    /** Descripción: Ejecuta la operación «sqlType» correspondiente a la responsabilidad de SqlDdlMetamodelMapper. Entradas: raw: dato de entrada necesario para la operación; enumIds: dato de entrada necesario para la operación; enumAliases: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaTypeReference sqlType(String raw,Map<String,String> enumIds,Map<String,String> enumAliases) {
        String type=raw==null?"":raw.trim(); String base=type.replaceAll("\\s*\\(.*$","").trim().toLowerCase(Locale.ROOT); String enumId=enumIds.get(key(type)); if(enumId==null)enumId=enumIds.get(key(base)); if(enumId==null)enumId=enumAliases.get(key(simple(base))); if(enumId!=null)return MetaTypeReference.enumType(enumId);
        if(base.startsWith("varchar")||base.startsWith("nvarchar")||base.startsWith("character varying")||base.equals("text")||base.equals("clob")||base.equals("uuid")||base.startsWith("char"))return MetaTypeReference.primitive(MetaPrimitiveType.STRING);
        if(base.equals("boolean")||base.equals("bool")||base.equals("bit"))return MetaTypeReference.primitive(MetaPrimitiveType.BOOLEAN);
        if(base.contains("tinyint"))return MetaTypeReference.primitive(MetaPrimitiveType.BYTE); if(base.contains("smallint"))return MetaTypeReference.primitive(MetaPrimitiveType.SHORT);
        if(base.equals("int")||base.equals("integer")||base.equals("serial")||base.equals("mediumint"))return MetaTypeReference.primitive(MetaPrimitiveType.INTEGER);
        if(base.equals("bigint")||base.equals("bigserial"))return MetaTypeReference.primitive(MetaPrimitiveType.LONG);
        if(base.startsWith("decimal")||base.startsWith("numeric")||base.startsWith("number")||base.equals("money"))return MetaTypeReference.primitive(MetaPrimitiveType.DECIMAL);
        if(base.equals("real")||base.equals("float"))return MetaTypeReference.primitive(MetaPrimitiveType.FLOAT); if(base.startsWith("double"))return MetaTypeReference.primitive(MetaPrimitiveType.DOUBLE);
        if(base.startsWith("date")||base.startsWith("time")||base.startsWith("timestamp")||base.equals("datetime")||base.equals("datetime2"))return MetaTypeReference.primitive(MetaPrimitiveType.DATE);
        if(base.contains("binary")||base.contains("blob")||base.equals("bytea")||base.equals("raw"))return MetaTypeReference.primitive(MetaPrimitiveType.BINARY);
        if(base.equals("json")||base.equals("jsonb")||base.equals("xml"))return MetaTypeReference.primitive(MetaPrimitiveType.OBJECT);
        return MetaTypeReference.external(type);
    }
    /** Descripción: Ejecuta la operación «unambiguousAliases» correspondiente a la responsabilidad de SqlDdlMetamodelMapper. Entradas: qualified: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,String> unambiguousAliases(Map<String,String> qualified) {
        Map<String,String> aliases=new LinkedHashMap<>(); java.util.Set<String> ambiguous=new java.util.HashSet<>();
        for(var entry:qualified.entrySet()){String alias=key(simple(entry.getKey()));if(aliases.putIfAbsent(alias,entry.getValue())!=null)ambiguous.add(alias);}
        for(String alias:ambiguous)aliases.remove(alias); return aliases;
    }
    /** Descripción: Ejecuta la operación «indexColumns» correspondiente a la responsabilidad de SqlDdlMetamodelMapper. Entradas: table: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String,Column> indexColumns(Table table){Map<String,Column> result=new LinkedHashMap<>();for(Column column:table.columns())result.put(key(column.name()),column);return result;}
    /** Descripción: Ejecuta la operación «joinKeys» correspondiente a la responsabilidad de SqlDdlMetamodelMapper. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String joinKeys(List<String> values){return values.isEmpty()?"":String.join(",",values);}
    /** Descripción: Ejecuta la operación «simple» correspondiente a la responsabilidad de SqlDdlMetamodelMapper. Entradas: qualified: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String simple(String qualified){int dot=qualified.lastIndexOf('.');return dot<0?qualified:qualified.substring(dot+1);}
    /** Descripción: Ejecuta la operación «key» correspondiente a la responsabilidad de SqlDdlMetamodelMapper. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String key(String value){return value==null?"":value.toLowerCase(Locale.ROOT);}
}
