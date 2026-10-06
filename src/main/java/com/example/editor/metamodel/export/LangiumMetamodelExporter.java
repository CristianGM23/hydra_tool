package com.example.editor.metamodel.export;

import java.util.List;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaRelation;

/**
 * Genera una gramática inicial Langium desde el núcleo neutral.
 */
@Component
public class LangiumMetamodelExporter implements MetamodelTextExporter {

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de LangiumMetamodelExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelExportFormat format() {
        return MetamodelExportFormat.LANGIUM;
    }

    @Override
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de LangiumMetamodelExporter. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String export(NeutralMetamodelExportContext context) {
        String grammarName = GrammarGenerationSupport.toPascalIdentifier(context.neutralMetamodel().name(), "GeneratedLanguage");
        StringBuilder result = new StringBuilder();
        var auxiliary = GrammarGenerationSupport.auxiliaryRuleNames(context, false);
        result.append("grammar ").append(grammarName).append("\n\n");
        result.append("// HYDRA_TRANSPORT_RULE ").append(auxiliary.rootRule()).append("\nentry ").append(auxiliary.rootRule()).append(":\n    elements+=").append(auxiliary.elementRule()).append("*;\n\n");
        result.append("// HYDRA_TRANSPORT_RULE ").append(auxiliary.elementRule()).append("\n");
        appendElementRule(result, context, auxiliary.elementRule());
        appendAbstractRules(result, context);

        for(MetaClass metaClass : context.neutralMetamodel().classes()) {
            if(metaClass.kind() == MetaClassKind.CLASS) {
                appendClassRule(result, context, metaClass);
            }
        }

        appendEnums(result, context);
        result.append("terminal ID: /[_a-zA-Z][\\w_]*/;\nterminal INT returns number: /[0-9]+/;\nterminal STRING: /\"([^\"\\\\]|\\\\.)*\"/;\nhidden terminal WS: /\\s+/;\nhidden terminal ML_COMMENT: /\\/\\*[\\s\\S]*?\\*\\//;\nhidden terminal SL_COMMENT: /\\/\\/[^\\n\\r]*/;\n");
        return result.toString();
    }

    /** Descripción: Ejecuta la operación «appendElementRule» correspondiente a la responsabilidad de LangiumMetamodelExporter. Entradas: result: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendElementRule(StringBuilder result, NeutralMetamodelExportContext context, String elementRule) {
        List<MetaClass> roots = GrammarGenerationSupport.findRootAlternatives(context);
        result.append(elementRule).append(":\n    ");

        for(int index = 0; index < roots.size(); index += 1) {
            if(index > 0) {
                result.append(" |\n    ");
            }

            result.append(ruleName(roots.get(index)));
        }

        result.append(";\n\n");
    }

    /** Descripción: Ejecuta la operación «appendAbstractRules» correspondiente a la responsabilidad de LangiumMetamodelExporter. Entradas: result: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendAbstractRules(StringBuilder result, NeutralMetamodelExportContext context) {
        for(MetaClass metaClass : context.neutralMetamodel().classes()) {
            if(metaClass.kind() == MetaClassKind.CLASS) {
                continue;
            }

            List<MetaClass> alternatives = GrammarGenerationSupport.findConcreteAssignableClasses(context, metaClass);

            if(!alternatives.isEmpty()) {
                result.append(ruleName(metaClass)).append(":\n    ");

                for(int index = 0; index < alternatives.size(); index += 1) {
                    if(index > 0) {
                        result.append(" |\n    ");
                    }

                    result.append(ruleName(alternatives.get(index)));
                }

                result.append(";\n\n");
            }
        }
    }

    /** Descripción: Ejecuta la operación «appendClassRule» correspondiente a la responsabilidad de LangiumMetamodelExporter. Entradas: result: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; metaClass: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendClassRule(StringBuilder result, NeutralMetamodelExportContext context, MetaClass metaClass) {
        MetaAttribute nameAttribute = MetamodelExportSupport.findNameAttribute(metaClass);
        result.append(ruleName(metaClass)).append(":\n    ").append(GrammarGenerationSupport.quotedKeyword(GrammarGenerationSupport.toKeyword(metaClass.name()))).append(" ");

        if(nameAttribute != null) {
            result.append("name=ID ");
        }

        result.append(GrammarGenerationSupport.quotedKeyword("{")).append("\n");

        for(MetaAttribute attribute : metaClass.attributes()) {
            if(attribute == nameAttribute) {
                continue;
            }

            result.append("    ").append(GrammarGenerationSupport.quotedKeyword(GrammarGenerationSupport.toKeyword(attribute.name()))).append(" ").append(attribute.name()).append(assignmentOperator(attribute.multiplicity())).append(typeRule(context, attribute)).append(multiplicityQuantifier(attribute.multiplicity())).append("\n");
        }

        for(MetaRelation relation : MetamodelExportSupport.structuralRelationsFrom(context, metaClass.id())) {
            MetaClass target = MetamodelExportSupport.indexClasses(context).get(relation.targetTypeId());
            result.append("    ").append(GrammarGenerationSupport.quotedKeyword(GrammarGenerationSupport.toKeyword(MetamodelExportSupport.text(relation.name(), "reference")))).append(" ");

            if(relation.kind() == com.example.editor.metamodel.core.MetaRelationKind.CONTAINMENT) {
                result.append(MetamodelExportSupport.camel(MetamodelExportSupport.text(relation.name(), "reference"), "reference")).append(assignmentOperator(relation.targetMultiplicity())).append(ruleName(target));
            } else {
                result.append(MetamodelExportSupport.camel(MetamodelExportSupport.text(relation.name(), "reference"), "reference")).append(assignmentOperator(relation.targetMultiplicity())).append("[").append(ruleName(target)).append(":ID]");
            }

            result.append(multiplicityQuantifier(relation.targetMultiplicity())).append("\n");
        }

        result.append("    ").append(GrammarGenerationSupport.quotedKeyword("}")).append(";\n\n");
    }

    /** Descripción: Ejecuta la operación «assignmentOperator» correspondiente a la responsabilidad de LangiumMetamodelExporter. Entradas: multiplicity: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String assignmentOperator(com.example.editor.metamodel.core.Multiplicity multiplicity) {
        return multiplicity != null && (multiplicity.unbounded() || multiplicity.upperBound() > 1) ? "+=" : "=";
    }

    /** Descripción: Traduce las multiplicidades representables de forma nativa por la gramática a su cuantificador sin alterar el operador de asignación. Entradas: multiplicity: multiplicidad neutral. Salida: devuelve ?, * o + cuando procede; las multiplicidades no representables quedan sin cuantificador y serán detectadas por la validación semántica estricta. */ private String multiplicityQuantifier(com.example.editor.metamodel.core.Multiplicity multiplicity) {
        if(multiplicity == null || (multiplicity.lowerBound() == 1 && Integer.valueOf(1).equals(multiplicity.upperBound()))) {
            return "";
        }
        if(multiplicity.lowerBound() == 0 && Integer.valueOf(1).equals(multiplicity.upperBound())) {
            return "?";
        }
        if(multiplicity.lowerBound() == 0 && multiplicity.unbounded()) {
            return "*";
        }
        if(multiplicity.lowerBound() == 1 && multiplicity.unbounded()) {
            return "+";
        }
        return "";
    }

    /** Descripción: Ejecuta la operación «typeRule» correspondiente a la responsabilidad de LangiumMetamodelExporter. Entradas: context: dato de entrada necesario para la operación; attribute: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String typeRule(NeutralMetamodelExportContext context, MetaAttribute attribute) {
        return switch(attribute.type().kind()) {
            case PRIMITIVE -> primitiveRule(attribute.type().targetId());
            case ENUM -> enumRuleName(context, attribute.type().targetId());
            case DATATYPE -> dataTypeRuleName(context, attribute.type().targetId());
            default -> "STRING";
        };
    }

    /** Descripción: Ejecuta la operación «enumRuleName» correspondiente a la responsabilidad de LangiumMetamodelExporter. Entradas: context: dato de entrada necesario para la operación; enumId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String enumRuleName(NeutralMetamodelExportContext context, String enumId) {
        var value = MetamodelExportSupport.indexEnums(context).get(enumId);
        return value == null ? GrammarGenerationSupport.toPascalIdentifier(enumId, "Enumeration") : GrammarGenerationSupport.toPascalIdentifier(value.name(), "Enumeration");
    }

    /** Descripción: Ejecuta la operación «dataTypeRuleName» correspondiente a la responsabilidad de LangiumMetamodelExporter. Entradas: context: dato de entrada necesario para la operación; dataTypeId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String dataTypeRuleName(NeutralMetamodelExportContext context, String dataTypeId) {
        var value = MetamodelExportSupport.indexDataTypes(context).get(dataTypeId);
        return value == null ? GrammarGenerationSupport.toPascalIdentifier(dataTypeId, "DataType") : GrammarGenerationSupport.toPascalIdentifier(value.name(), "DataType");
    }

    /** Descripción: Ejecuta la operación «primitiveRule» correspondiente a la responsabilidad de LangiumMetamodelExporter. Entradas: primitive: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String primitiveRule(String primitive) {
        return switch(primitive) {
            case "BOOLEAN" -> "('true' | 'false')";
            case "BYTE", "SHORT", "INTEGER", "LONG", "BIG_INTEGER" -> "INT";
            default -> "STRING";
        };
    }

    /** Descripción: Ejecuta la operación «appendEnums» correspondiente a la responsabilidad de LangiumMetamodelExporter. Entradas: result: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendEnums(StringBuilder result, NeutralMetamodelExportContext context) {
        context.neutralMetamodel().enums().forEach(item -> {
            result.append(GrammarGenerationSupport.toPascalIdentifier(item.name(), "Enumeration")).append(" returns string:\n    ");

            for(int index = 0; index < item.literals().size(); index += 1) {
                if(index > 0) {
                    result.append(" | ");
                }

                result.append(GrammarGenerationSupport.quotedKeyword(item.literals().get(index).name()));
            }

            result.append(";\n\n");
        });

        context.neutralMetamodel().dataTypes().forEach(item -> result.append(GrammarGenerationSupport.toPascalIdentifier(item.name(), "DataType")).append(" returns string:\n    STRING;\n\n"));
    }

    /** Descripción: Ejecuta la operación «ruleName» correspondiente a la responsabilidad de LangiumMetamodelExporter. Entradas: metaClass: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String ruleName(MetaClass metaClass) {
        if(metaClass == null) {
            return "Element";
        }

        return GrammarGenerationSupport.toPascalIdentifier(metaClass.name(), "Element");
    }
}
