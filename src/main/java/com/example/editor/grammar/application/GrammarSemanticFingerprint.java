package com.example.editor.grammar.application;

import java.util.*;
import com.example.editor.grammar.core.GrammarHashing;
import com.example.editor.metamodel.core.*;

/** Calcula una huella estructural independiente de identificadores internos y metadatos de transporte. */
final class GrammarSemanticFingerprint {
    /** Descripción: Inicializa una instancia de GrammarSemanticFingerprint con sus dependencias y datos de entrada. Entradas: no requiere parámetros de entrada. Salida: deja la instancia inicializada y lista para su uso. */ private GrammarSemanticFingerprint() { }

    /** Descripción: Calcula una huella estable del significado estructural que una conversión de gramática debe preservar. Entradas: metamodel: metamodelo neutral inferido. Salida: devuelve un SHA-256 sobre clases, atributos, operaciones, enumeraciones, tipos de datos y relaciones normalizados por nombre. */
    /** Descripción: Ejecuta la operación «fingerprint» correspondiente a la responsabilidad de GrammarSemanticFingerprint. Entradas: metamodel: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ static String fingerprint(MetaModel metamodel) {
        Objects.requireNonNull(metamodel, "metamodel");
        Map<String,String> typeNames=new HashMap<>();
        for(MetaClass item:metamodel.classes())typeNames.put(item.id(),"CLASS:"+item.name());
        for(MetaEnum item:metamodel.enums())typeNames.put(item.id(),"ENUM:"+item.name());
        for(MetaDataType item:metamodel.dataTypes())typeNames.put(item.id(),"DATATYPE:"+item.name());
        List<String> lines=new ArrayList<>();
        for(MetaClass item:metamodel.classes()){
            lines.add("C|"+item.name()+"|"+item.kind());
            for(MetaAttribute attribute:item.attributes())lines.add("A|"+item.name()+"|"+attribute.name()+"|"+type(attribute.type(),typeNames)+"|"+multiplicity(attribute.multiplicity())+"|"+attribute.ordered()+"|"+attribute.unique()+"|"+attribute.defaultValue());
            for(MetaOperation operation:item.operations()){
                StringBuilder b=new StringBuilder("O|").append(item.name()).append('|').append(operation.name()).append('|').append(type(operation.returnType(),typeNames)).append('|').append(multiplicity(operation.returnMultiplicity())).append('|').append(operation.ordered()).append('|').append(operation.unique()).append('|').append(operation.behaviorKind());
                for(MetaParameter parameter:operation.parameters())b.append("|P:").append(parameter.name()).append(':').append(type(parameter.type(),typeNames)).append(':').append(multiplicity(parameter.multiplicity())).append(':').append(parameter.ordered()).append(':').append(parameter.unique());
                for(MetaTypeReference exception:operation.exceptionTypes())b.append("|E:").append(type(exception,typeNames));
                lines.add(b.toString());
            }
        }
        for(MetaEnum item:metamodel.enums()){
            StringBuilder b=new StringBuilder("N|").append(item.name());
            item.literals().stream().sorted(Comparator.comparingInt(MetaEnumLiteral::value).thenComparing(MetaEnumLiteral::name)).forEach(literal->b.append('|').append(literal.name()).append('=').append(literal.value()));
            lines.add(b.toString());
        }
        for(MetaDataType item:metamodel.dataTypes())lines.add("D|"+item.name());
        for(MetaRelation relation:metamodel.relations())lines.add("R|"+resolve(relation.sourceTypeId(),typeNames)+"|"+relation.name()+"|"+resolve(relation.targetTypeId(),typeNames)+"|"+relation.kind()+"|"+multiplicity(relation.sourceMultiplicity())+"|"+multiplicity(relation.targetMultiplicity())+"|"+relation.ordered()+"|"+relation.unique()+"|"+opposite(relation,metamodel.relations()));
        Collections.sort(lines);
        return GrammarHashing.sha256(String.join("\n",lines));
    }

    /** Descripción: Ejecuta la operación «opposite» correspondiente a la responsabilidad de GrammarSemanticFingerprint. Entradas: relation: dato de entrada necesario para la operación; relations: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String opposite(MetaRelation relation,List<MetaRelation> relations){
        if(relation.oppositeRelationId()==null||relation.oppositeRelationId().isBlank())return "";
        return relations.stream().filter(candidate->candidate.id().equals(relation.oppositeRelationId())).map(MetaRelation::name).findFirst().orElse("?");
    }
    /** Descripción: Obtiene la información correspondiente a «resolve» dentro de GrammarSemanticFingerprint. Entradas: id: identificador que delimita el recurso objetivo; names: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String resolve(String id,Map<String,String> names){return names.getOrDefault(id,id==null?"":id);}
    /** Descripción: Ejecuta la operación «type» correspondiente a la responsabilidad de GrammarSemanticFingerprint. Entradas: type: dato de entrada necesario para la operación; names: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String type(MetaTypeReference type,Map<String,String> names){
        if(type==null)return "VOID";
        return type.kind()+":"+(type.externalName()!=null&&!type.externalName().isBlank()?type.externalName():resolve(type.targetId(),names));
    }
    /** Descripción: Ejecuta la operación «multiplicity» correspondiente a la responsabilidad de GrammarSemanticFingerprint. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String multiplicity(Multiplicity value){return value==null?"-":value.lowerBound()+".."+(value.upperBound()==null?"*":value.upperBound());}
}
