package com.example.editor.metamodel.export;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaRelation;

/**
 * Genera una gramática combinada ANTLR 4 desde el núcleo neutral.
 */
@Component
public class AntlrMetamodelExporter implements MetamodelTextExporter {

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de AntlrMetamodelExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelExportFormat format() {
        return MetamodelExportFormat.ANTLR;
    }

    @Override
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de AntlrMetamodelExporter. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String export(NeutralMetamodelExportContext context) {
        String grammarName = GrammarGenerationSupport.toPascalIdentifier(context.neutralMetamodel().name(), "GeneratedLanguage");
        StringBuilder result = new StringBuilder();
        var auxiliary = GrammarGenerationSupport.auxiliaryRuleNames(context, true);
        result.append("grammar ").append(grammarName).append(";\n\n").append(auxiliary.rootRule()).append("\n    : ").append(auxiliary.elementRule()).append("* EOF\n    ;\n\n").append(auxiliary.elementRule()).append("\n    : ");
        var roots = GrammarGenerationSupport.findRootAlternatives(context);

        for(int index = 0; index < roots.size(); index += 1) {
            if(index > 0) {
                result.append(" | ");
            }

            result.append(ruleName(roots.get(index)));
        }

        result.append(";\n\n");

        for(MetaClass metaClass : context.neutralMetamodel().classes()) {
            if(metaClass.kind() != MetaClassKind.CLASS) {
                continue;
            }

            result.append(ruleName(metaClass)).append(": '").append(GrammarGenerationSupport.toKeyword(metaClass.name())).append("' ");
            if(MetamodelExportSupport.findNameAttribute(metaClass) != null) result.append("ID ");
            result.append("'{' ");

            for(MetaAttribute attribute : metaClass.attributes()) {
                if("name".equalsIgnoreCase(attribute.name())) {
                    continue;
                }

                result.append("('").append(GrammarGenerationSupport.toKeyword(attribute.name())).append("' ").append(typeRule(context, attribute)).append(")").append(quantifier(attribute.multiplicity())).append(" ");
            }

            for(MetaRelation relation : MetamodelExportSupport.structuralRelationsFrom(context, metaClass.id())) {
                MetaClass target = MetamodelExportSupport.indexClasses(context).get(relation.targetTypeId());
                boolean containment = relation.kind() == com.example.editor.metamodel.core.MetaRelationKind.CONTAINMENT;
                String targetRule = containment ? ruleName(target) : "ID";
                result.append("('").append(GrammarGenerationSupport.toKeyword(MetamodelExportSupport.text(relation.name(), "reference"))).append("' ").append(targetRule);
                if(!containment && target != null) result.append(" /* HYDRA_REFERENCE ").append(ruleName(target)).append(" */");
                result.append(")").append(quantifier(relation.targetMultiplicity())).append(" ");
            }

            result.append("'}';\n\n");
        }

        context.neutralMetamodel().enums().forEach(item -> {
            result.append(GrammarGenerationSupport.toCamelIdentifier(item.name(), "enumeration")).append("\n    : ");

            for(int index = 0; index < item.literals().size(); index += 1) {
                if(index > 0) {
                    result.append("\n    | ");
                }

                result.append("'").append(item.literals().get(index).name()).append("'");
            }

            result.append("\n    ;\n\n");
        });

        result.append("ID\n    : [a-zA-Z_] [a-zA-Z_0-9]*\n    ;\nINT\n    : [0-9]+\n    ;\nSTRING\n    : '\"' (~[\"\\\\] | '\\\\' .)* '\"'\n    ;\nWS\n    : [ \\t\\r\\n]+ -> skip\n    ;\nLINE_COMMENT\n    : '//' ~[\\r\\n]* -> skip\n    ;\nBLOCK_COMMENT\n    : '/*' .*? '*/' -> skip\n    ;\n");
        return result.toString();
    }

    /** Descripción: Ejecuta la operación «typeRule» correspondiente a la responsabilidad de AntlrMetamodelExporter. Entradas: context: dato de entrada necesario para la operación; attribute: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String typeRule(NeutralMetamodelExportContext context, MetaAttribute attribute) {
        return switch(attribute.type().kind()) {
            case PRIMITIVE -> switch(attribute.type().targetId()) {
                case "BOOLEAN" -> "('true' | 'false')";
                case "BYTE", "SHORT", "INTEGER", "LONG", "BIG_INTEGER" -> "INT";
                default -> "STRING";
            };
            case ENUM -> GrammarGenerationSupport.toCamelIdentifier(MetamodelExportSupport.resolveTypeName(context, attribute.type()), "enumeration");
            default -> "STRING";
        };
    }

    /** Descripción: Ejecuta la operación «quantifier» correspondiente a la responsabilidad de AntlrMetamodelExporter. Entradas: multiplicity: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String quantifier(com.example.editor.metamodel.core.Multiplicity multiplicity) {
        if(multiplicity == null) {
            return "?";
        }

        if(multiplicity.unbounded() || multiplicity.upperBound() > 1) {
            return multiplicity.lowerBound() == 0 ? "*" : "+";
        }

        return multiplicity.lowerBound() == 0 ? "?" : "";
    }

    /** Descripción: Ejecuta la operación «ruleName» correspondiente a la responsabilidad de AntlrMetamodelExporter. Entradas: metaClass: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String ruleName(MetaClass metaClass) {
        return metaClass == null ? "element" : GrammarGenerationSupport.toCamelIdentifier(metaClass.name(), "element");
    }
}
