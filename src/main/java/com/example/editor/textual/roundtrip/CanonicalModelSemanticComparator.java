package com.example.editor.textual.roundtrip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;
import com.example.editor.validation.model.CanonicalModelDocument;
import com.example.editor.validation.model.CanonicalModelElement;
import com.example.editor.validation.model.CanonicalModelRelation;

/** Compara el significado de instancias canónicas y valida sus invariantes frente al metamodelo neutral. */
@Component
public class CanonicalModelSemanticComparator {
    /** Descripción: Compara raíces, elementos, atributos y relaciones preservando orden cuando el metamodelo declara una relación ordenada. Entradas: metamodel: metamodelo neutral; expected: instancia esperada; actual: instancia reconstruida. Salida: devuelve equivalencia semántica y diferencias estables sin comparar IDs efímeros de relaciones ni anotaciones de transporte. */
    public CanonicalModelSemanticReport compare(MetaModel metamodel, CanonicalModelDocument expected, CanonicalModelDocument actual) {
        List<String> differences = new ArrayList<>();
        if(expected == null || actual == null) {
            differences.add("MODEL_UNAVAILABLE");
            return new CanonicalModelSemanticReport(false, differences);
        }
        if(!Objects.equals(expected.metamodelId(), actual.metamodelId())) differences.add("METAMODEL_ID:"+expected.metamodelId()+"!="+actual.metamodelId());
        if(!expected.rootElementIds().equals(actual.rootElementIds())) differences.add("ROOTS:"+expected.rootElementIds()+"!="+actual.rootElementIds());
        Map<String,CanonicalModelElement> left = elementIndex(expected), right = elementIndex(actual);
        if(!left.keySet().equals(right.keySet())) differences.add("ELEMENT_IDS:"+left.keySet()+"!="+right.keySet());
        for(String id : intersection(left.keySet(), right.keySet())) {
            CanonicalModelElement a=left.get(id), b=right.get(id);
            if(!Objects.equals(a.classId(), b.classId())) differences.add("CLASS:"+id+":"+a.classId()+"!="+b.classId());
            Map<String,Object> aAttributes=normalizedAttributes(metamodel,a.classId(),a.attributes()), bAttributes=normalizedAttributes(metamodel,b.classId(),b.attributes());
            if(!Objects.equals(aAttributes, bAttributes)) differences.add("ATTRIBUTES:"+id+":"+aAttributes+"!="+bAttributes);
        }
        Map<String,MetaRelation> relationTypes = new LinkedHashMap<>();
        if(metamodel != null) metamodel.relations().forEach(item -> relationTypes.put(item.id(), item));
        Map<RelationKey,List<String>> leftRelations=relationTargets(expected), rightRelations=relationTargets(actual);
        LinkedHashSet<RelationKey> keys=new LinkedHashSet<>();keys.addAll(leftRelations.keySet());keys.addAll(rightRelations.keySet());
        for(RelationKey key:keys) {
            List<String> a=leftRelations.getOrDefault(key,List.of()), b=rightRelations.getOrDefault(key,List.of());
            MetaRelation declaration=relationTypes.get(key.relationId());
            boolean ordered=declaration != null && declaration.ordered();
            if(ordered ? !a.equals(b) : !new LinkedHashSet<>(a).equals(new LinkedHashSet<>(b))) differences.add("RELATION:"+key+":"+a+"!="+b);
        }
        return new CanonicalModelSemanticReport(differences.isEmpty(), differences);
    }

    /** Descripción: Valida una instancia contra el metamodelo sin modificarla, incluyendo referencias, cardinalidades, contención acíclica, único contenedor y raíces reales. Entradas: metamodel: contrato neutral; model: instancia canónica. Salida: devuelve todas las violaciones estructurales detectadas para poder fallar de forma cerrada. */
    public CanonicalModelInvariantReport validate(MetaModel metamodel, CanonicalModelDocument model) {
        List<String> violations=new ArrayList<>();
        if(metamodel == null || model == null) return new CanonicalModelInvariantReport(false,List.of("MODEL_OR_METAMODEL_UNAVAILABLE"));
        Map<String,CanonicalModelElement> elements=elementIndex(model);
        if(elements.size()!=model.elements().size()) violations.add("DUPLICATE_ELEMENT_ID");
        Set<String> relationInstanceIds=new LinkedHashSet<>();
        Map<String,MetaRelation> declarations=new LinkedHashMap<>();metamodel.relations().forEach(item->declarations.put(item.id(),item));
        Map<String,String> containmentParent=new LinkedHashMap<>();
        Map<String,List<String>> containmentChildren=new LinkedHashMap<>();
        Map<RelationKey,Integer> counts=new LinkedHashMap<>();
        for(String root:model.rootElementIds()) if(!elements.containsKey(root)) violations.add("ROOT_NOT_FOUND:"+root);
        for(CanonicalModelElement element:model.elements()) {
            var type=metamodel.classes().stream().filter(item->item.id().equals(element.classId())).findFirst().orElse(null);
            if(type==null){violations.add("ELEMENT_CLASS_NOT_FOUND:"+element.id()+":"+element.classId());continue;}
            Map<String,MetaAttribute> declared=effectiveAttributes(metamodel,type.id(),new LinkedHashSet<>());
            for(String attributeId:element.attributes().keySet()) if(!declared.containsKey(attributeId)) violations.add("ATTRIBUTE_DECLARATION_NOT_FOUND:"+element.id()+":"+attributeId);
            for(MetaAttribute attribute:declared.values()) {
                Object value=element.attributes().get(attribute.id());int count=value==null?0:value instanceof List<?> list?list.size():1;
                int lower=attribute.multiplicity()==null?0:attribute.multiplicity().lowerBound();Integer upper=attribute.multiplicity()==null?null:attribute.multiplicity().upperBound();
                if(count<lower) violations.add("ATTRIBUTE_BELOW_LOWER_BOUND:"+element.id()+":"+attribute.id()+":"+count+"<"+lower);
                if(upper!=null&&count>upper) violations.add("ATTRIBUTE_ABOVE_UPPER_BOUND:"+element.id()+":"+attribute.id()+":"+count+">"+upper);
                if(value!=null&&!attributeValueCompatible(metamodel,attribute,value)) violations.add("ATTRIBUTE_TYPE_MISMATCH:"+element.id()+":"+attribute.id());
                if(attribute.unique()&&value instanceof List<?> list&&new LinkedHashSet<>(list).size()!=list.size()) violations.add("NON_UNIQUE_ATTRIBUTE:"+element.id()+":"+attribute.id());
            }
        }
        for(CanonicalModelRelation relation:model.relations()) {
            if(!relationInstanceIds.add(relation.id())) violations.add("DUPLICATE_RELATION_INSTANCE_ID:"+relation.id());
            CanonicalModelElement source=elements.get(relation.sourceElementId()), target=elements.get(relation.targetElementId());
            if(source==null) violations.add("RELATION_SOURCE_NOT_FOUND:"+relation.id());
            if(target==null) violations.add("RELATION_TARGET_NOT_FOUND:"+relation.id());
            MetaRelation declaration=declarations.get(relation.relationId());
            if(declaration==null) {violations.add("RELATION_DECLARATION_NOT_FOUND:"+relation.relationId());continue;}
            if(source!=null && !isAssignable(metamodel,source.classId(),declaration.sourceTypeId(),new LinkedHashSet<>())) violations.add("RELATION_SOURCE_TYPE:"+relation.id());
            if(target!=null && !isAssignable(metamodel,target.classId(),declaration.targetTypeId(),new LinkedHashSet<>())) violations.add("RELATION_TARGET_TYPE:"+relation.id());
            counts.merge(new RelationKey(relation.sourceElementId(),relation.relationId()),1,Integer::sum);
            if(declaration.kind()==com.example.editor.metamodel.core.MetaRelationKind.CONTAINMENT && target!=null) {
                String previous=containmentParent.putIfAbsent(target.id(),source==null?"":source.id());
                if(previous!=null && !previous.equals(source==null?"":source.id())) violations.add("MULTIPLE_CONTAINERS:"+target.id());
                if(source!=null) containmentChildren.computeIfAbsent(source.id(),ignored->new ArrayList<>()).add(target.id());
            }
        }
        for(CanonicalModelElement element:model.elements()) for(MetaRelation declaration:metamodel.relations()) if(declaration.sourceTypeId().equals(element.classId()) && isStructural(declaration)) {
            int count=counts.getOrDefault(new RelationKey(element.id(),declaration.id()),0);
            int lower=declaration.targetMultiplicity()==null?0:declaration.targetMultiplicity().lowerBound();
            Integer upper=declaration.targetMultiplicity()==null?null:declaration.targetMultiplicity().upperBound();
            if(count<lower) violations.add("RELATION_BELOW_LOWER_BOUND:"+element.id()+":"+declaration.id()+":"+count+"<"+lower);
            if(upper!=null && count>upper) violations.add("RELATION_ABOVE_UPPER_BOUND:"+element.id()+":"+declaration.id()+":"+count+">"+upper);
            if(declaration.unique()) {
                List<String> targets=relationTargets(model).getOrDefault(new RelationKey(element.id(),declaration.id()),List.of());
                if(new LinkedHashSet<>(targets).size()!=targets.size()) violations.add("NON_UNIQUE_RELATION:"+element.id()+":"+declaration.id());
            }
        }
        Set<String> expectedRoots=new LinkedHashSet<>(elements.keySet());expectedRoots.removeAll(containmentParent.keySet());
        if(!new LinkedHashSet<>(model.rootElementIds()).equals(expectedRoots)) violations.add("ROOTS_DO_NOT_MATCH_CONTAINMENT:"+expectedRoots);
        for(String elementId:elements.keySet()) if(hasContainmentCycle(elementId,containmentChildren,new LinkedHashSet<>(),new LinkedHashSet<>())) {violations.add("CONTAINMENT_CYCLE:"+elementId);break;}
        return new CanonicalModelInvariantReport(violations.isEmpty(),violations);
    }


    /** Descripción: Normaliza atributos de comparación eliminando colecciones vacías opcionales, equivalentes a ausencia de asignación en la gramática generada. Entradas: model, classId y attributes. Salida: devuelve un mapa semántico estable sin diferencias de transporte para multiplicidades many vacías. */ private static Map<String,Object> normalizedAttributes(MetaModel model,String classId,Map<String,Object> attributes){Map<String,Object> result=new LinkedHashMap<>();Map<String,MetaAttribute> declared=model==null?Map.of():effectiveAttributes(model,classId,new LinkedHashSet<>());if(attributes!=null)for(var entry:attributes.entrySet()){MetaAttribute attribute=declared.get(entry.getKey());if(attribute!=null&&attribute.multiplicity()!=null&&(attribute.multiplicity().unbounded()||attribute.multiplicity().upperBound()>1)&&entry.getValue() instanceof List<?> list&&list.isEmpty())continue;result.put(entry.getKey(),entry.getValue());}return result;}
    /** Descripción: Reúne atributos propios e heredados de una clase evitando ciclos de herencia. Entradas: model, classId y visited. Salida: devuelve atributos efectivos indexados por ID. */ private static Map<String,MetaAttribute> effectiveAttributes(MetaModel model,String classId,Set<String> visited){Map<String,MetaAttribute> result=new LinkedHashMap<>();if(!visited.add(classId))return result;for(MetaRelation relation:model.relations())if(relation.sourceTypeId().equals(classId)&&(relation.kind()==com.example.editor.metamodel.core.MetaRelationKind.INHERITANCE||relation.kind()==com.example.editor.metamodel.core.MetaRelationKind.IMPLEMENTATION))result.putAll(effectiveAttributes(model,relation.targetTypeId(),visited));model.classes().stream().filter(item->item.id().equals(classId)).findFirst().ifPresent(type->type.attributes().forEach(attribute->result.put(attribute.id(),attribute)));return result;}
    /** Descripción: Comprueba compatibilidad de valores escalares o listas con el tipo neutral declarado. Entradas: model, attribute y value. Salida: devuelve true cuando todos los valores son representables por el tipo. */ private static boolean attributeValueCompatible(MetaModel model,MetaAttribute attribute,Object value){List<?> values=value instanceof List<?> list?list:List.of(value);for(Object item:values)if(!scalarCompatible(model,attribute,item))return false;return true;}
    /** Descripción: Comprueba un valor escalar contra primitivas, enums o datatypes canónicos sin coerciones silenciosas. Entradas: model, attribute e item. Salida: devuelve true cuando el valor respeta el contrato de tipo. */ private static boolean scalarCompatible(MetaModel model,MetaAttribute attribute,Object item){if(item==null||attribute.type()==null)return item==null;if(attribute.type().kind()==MetaTypeReferenceKind.ENUM){MetaEnum value=model.enums().stream().filter(e->e.id().equals(attribute.type().targetId())).findFirst().orElse(null);return value!=null&&value.literals().stream().anyMatch(literal->literal.name().equals(String.valueOf(item)));}if(attribute.type().kind()==MetaTypeReferenceKind.DATATYPE)return item instanceof String;if(attribute.type().kind()!=MetaTypeReferenceKind.PRIMITIVE)return true;return switch(attribute.type().targetId()){case "STRING","CHARACTER","DATE","BINARY","OBJECT"->item instanceof String;case "BOOLEAN"->item instanceof Boolean;case "BYTE","SHORT","INTEGER","LONG","BIG_INTEGER"->item instanceof Byte||item instanceof Short||item instanceof Integer||item instanceof Long||item instanceof java.math.BigInteger;case "FLOAT","DOUBLE","DECIMAL"->item instanceof Number;default->true;};}

    /** Descripción: Construye un índice estable de elementos por ID. Entradas: model: instancia canónica. Salida: devuelve un mapa en orden de aparición. */ private static Map<String,CanonicalModelElement> elementIndex(CanonicalModelDocument model){Map<String,CanonicalModelElement> result=new LinkedHashMap<>();if(model!=null)model.elements().forEach(item->result.put(item.id(),item));return result;}
    /** Descripción: Agrupa destinos por elemento origen y relación declarada. Entradas: model: instancia canónica. Salida: devuelve listas de destinos conservando el orden canónico de las relaciones. */ private static Map<RelationKey,List<String>> relationTargets(CanonicalModelDocument model){Map<RelationKey,List<String>> result=new LinkedHashMap<>();if(model!=null)for(CanonicalModelRelation item:model.relations())result.computeIfAbsent(new RelationKey(item.sourceElementId(),item.relationId()),ignored->new ArrayList<>()).add(item.targetElementId());return result;}
    /** Descripción: Calcula la intersección ordenada de dos conjuntos de IDs. Entradas: left y right: conjuntos de identificadores. Salida: devuelve únicamente IDs presentes en ambos. */ private static Set<String> intersection(Set<String> left,Set<String> right){LinkedHashSet<String> result=new LinkedHashSet<>(left);result.retainAll(right);return result;}
    /** Descripción: Determina si una relación forma parte de la estructura serializable del modelo. Entradas: relation: declaración neutral. Salida: devuelve true para referencia, agregación o contención. */ private static boolean isStructural(MetaRelation relation){return relation.kind()==com.example.editor.metamodel.core.MetaRelationKind.REFERENCE||relation.kind()==com.example.editor.metamodel.core.MetaRelationKind.AGGREGATION||relation.kind()==com.example.editor.metamodel.core.MetaRelationKind.CONTAINMENT;}
    /** Descripción: Comprueba asignabilidad transitiva mediante herencia o implementación. Entradas: model, candidateId, targetId y visited. Salida: devuelve true si la clase candidata puede ocupar el tipo destino. */ private static boolean isAssignable(MetaModel model,String candidateId,String targetId,Set<String> visited){if(Objects.equals(candidateId,targetId))return true;if(!visited.add(candidateId))return false;for(MetaRelation relation:model.relations())if(relation.sourceTypeId().equals(candidateId)&&(relation.kind()==com.example.editor.metamodel.core.MetaRelationKind.INHERITANCE||relation.kind()==com.example.editor.metamodel.core.MetaRelationKind.IMPLEMENTATION)&&isAssignable(model,relation.targetTypeId(),targetId,visited))return true;return false;}
    /** Descripción: Detecta ciclos en el grafo de contención con una búsqueda en profundidad acotada por los elementos del modelo. Entradas: current: elemento actual; children: adyacencias; visiting: pila activa; visited: nodos finalizados. Salida: devuelve true si existe un ciclo alcanzable. */ private static boolean hasContainmentCycle(String current,Map<String,List<String>> children,Set<String> visiting,Set<String> visited){if(visited.contains(current))return false;if(!visiting.add(current))return true;for(String child:children.getOrDefault(current,List.of()))if(hasContainmentCycle(child,children,visiting,visited))return true;visiting.remove(current);visited.add(current);return false;}
    private record RelationKey(String sourceId,String relationId){}
}
