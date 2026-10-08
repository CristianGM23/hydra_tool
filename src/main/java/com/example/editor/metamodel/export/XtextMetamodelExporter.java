package com.example.editor.metamodel.export;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaRelation;

/**
 * Genera una gramática inicial Xtext desde el núcleo neutral.
 */
@Component
public class XtextMetamodelExporter implements MetamodelTextExporter {

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de XtextMetamodelExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelExportFormat format() {
        return MetamodelExportFormat.XTEXT;
    }

    @Override
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de XtextMetamodelExporter. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String export(NeutralMetamodelExportContext context) {
        String grammarName = GrammarGenerationSupport.toPascalIdentifier(context.neutralMetamodel().name(), "GeneratedLanguage");
        StringBuilder result = new StringBuilder();
        var auxiliary = GrammarGenerationSupport.auxiliaryRuleNames(context, false);
        MetaClass semanticRoot = GrammarGenerationSupport.findSemanticRoot(context);
        result.append("grammar generated.").append(grammarName).append(" with org.eclipse.xtext.common.Terminals\n\n");
        if(semanticRoot != null) result.append("// HYDRA_SEMANTIC_ROOT ").append(ruleName(semanticRoot)).append("\n");
        result.append("import \"").append(context.targets().ecore().namespaceUri()).append("\" as model\n\n");
        result.append("// HYDRA_TRANSPORT_RULE ").append(auxiliary.rootRule()).append("\n").append(auxiliary.rootRule()).append(":\n    elements+=").append(auxiliary.elementRule()).append("*;\n\n");
        result.append("// HYDRA_TRANSPORT_RULE ").append(auxiliary.elementRule()).append("\n").append(auxiliary.elementRule()).append(":\n    ");
        var roots = GrammarGenerationSupport.findRootAlternatives(context);

        for(int index = 0; index < roots.size(); index += 1) {
            if(index > 0) {
                result.append(" |\n    ");
            }

            result.append(ruleName(roots.get(index)));
        }

        result.append(";\n\n");
        appendAbstractRules(result, context);

        for(MetaClass metaClass : context.neutralMetamodel().classes()) {
            if(metaClass.kind() != MetaClassKind.CLASS) {
                continue;
            }

            result.append(ruleName(metaClass)).append(" returns model::").append(metaClass.name()).append(":\n    ").append(GrammarGenerationSupport.quotedKeyword(GrammarGenerationSupport.toKeyword(metaClass.name()))).append(" ");
            MetaAttribute nameAttribute = MetamodelExportSupport.findNameAttribute(metaClass);

            if(nameAttribute != null) {
                result.append("name=ID ");
            }

            result.append("'{' \n");

            for(MetaAttribute attribute : metaClass.attributes()) {
                if(attribute == nameAttribute) {
                    continue;
                }

                result.append("    '").append(GrammarGenerationSupport.toKeyword(attribute.name())).append("' ").append(attribute.name()).append(assignmentOperator(attribute.multiplicity())).append(typeRule(context, attribute)).append(multiplicityQuantifier(attribute.multiplicity())).append("\n");
            }

            for(MetaRelation relation : MetamodelExportSupport.structuralRelationsFrom(context, metaClass.id())) {
                MetaClass target = MetamodelExportSupport.indexClasses(context).get(relation.targetTypeId());
                result.append("    '").append(GrammarGenerationSupport.toKeyword(MetamodelExportSupport.text(relation.name(), "reference"))).append("' ").append(MetamodelExportSupport.camel(MetamodelExportSupport.text(relation.name(), "reference"), "reference")).append(assignmentOperator(relation.targetMultiplicity()));

                if(relation.kind() == com.example.editor.metamodel.core.MetaRelationKind.CONTAINMENT) {
                    result.append(ruleName(target, auxiliary.elementRule()));
                } else {
                    result.append("[model::").append(target == null ? "EObject" : target.name()).append("|ID]");
                }

                result.append(multiplicityQuantifier(relation.targetMultiplicity())).append("\n");
            }

            result.append("    '}' ;\n\n");
        }

        context.neutralMetamodel().enums().forEach(item -> {
            result.append("enum ").append(GrammarGenerationSupport.toPascalIdentifier(item.name(), "Enumeration")).append(" returns model::").append(item.name()).append(":\n    ");

            for(int index = 0; index < item.literals().size(); index += 1) {
                if(index > 0) {
                    result.append(" | ");
                }

                result.append(item.literals().get(index).name()).append("='").append(item.literals().get(index).name()).append("'");
            }

            result.append(";\n\n");
        });

        context.neutralMetamodel().dataTypes().forEach(item -> result.append(GrammarGenerationSupport.toPascalIdentifier(item.name(), "DataType")).append(" returns model::").append(item.name()).append(":\n    STRING;\n\n"));
        return result.toString();
    }

    /** Descripción: Ejecuta la operación «appendAbstractRules» correspondiente a la responsabilidad de XtextMetamodelExporter. Entradas: result: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendAbstractRules(StringBuilder result, NeutralMetamodelExportContext context) {
        for(MetaClass metaClass : context.neutralMetamodel().classes()) {
            if(metaClass.kind() == MetaClassKind.CLASS) {
                continue;
            }

            var alternatives = GrammarGenerationSupport.findConcreteAssignableClasses(context, metaClass);

            if(!alternatives.isEmpty()) {
                result.append(ruleName(metaClass)).append(" returns model::").append(metaClass.name()).append(":\n    ");

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

    /** Descripción: Ejecuta la operación «assignmentOperator» correspondiente a la responsabilidad de XtextMetamodelExporter. Entradas: multiplicity: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String assignmentOperator(com.example.editor.metamodel.core.Multiplicity multiplicity) {
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

    /** Descripción: Ejecuta la operación «typeRule» correspondiente a la responsabilidad de XtextMetamodelExporter. Entradas: context: dato de entrada necesario para la operación; attribute: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String typeRule(NeutralMetamodelExportContext context, MetaAttribute attribute) {
        return switch(attribute.type().kind()) {
            case PRIMITIVE -> switch(attribute.type().targetId()) {
                case "BOOLEAN" -> "('true' | 'false')";
                case "BYTE", "SHORT", "INTEGER", "LONG", "BIG_INTEGER" -> "INT";
                default -> "STRING";
            };
            case ENUM, DATATYPE -> GrammarGenerationSupport.toPascalIdentifier(MetamodelExportSupport.resolveTypeName(context, attribute.type()), "Value");
            default -> "STRING";
        };
    }

    /** Descripción: Ejecuta la operación «ruleName» correspondiente a la responsabilidad de XtextMetamodelExporter. Entradas: metaClass: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String ruleName(MetaClass metaClass) {
        return ruleName(metaClass, "Element");
    }

    /** Descripción: Resuelve el nombre de una regla de clase y utiliza el nombre auxiliar libre de colisiones cuando no existe una clase destino concreta. Entradas: metaClass: clase destino; fallback: regla auxiliar elegida para esta exportación. Salida: devuelve el nombre de regla que debe emitir Xtext. */ private String ruleName(MetaClass metaClass, String fallback) {
        return metaClass == null ? fallback : GrammarGenerationSupport.toPascalIdentifier(metaClass.name(), "Element");
    }
}
