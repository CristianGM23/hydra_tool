package com.example.editor.metamodel.export;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaParameter;

/**
 * Genera una representación XML neutral.
 */
@Component
public class NeutralXmlMetamodelExporter implements MetamodelTextExporter {

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de NeutralXmlMetamodelExporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelExportFormat format() {
        return MetamodelExportFormat.XML;
    }

    @Override
    /** Descripción: Ejecuta la operación «export» correspondiente a la responsabilidad de NeutralXmlMetamodelExporter. Entradas: context: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String export(NeutralMetamodelExportContext context) {
        StringBuilder result = new StringBuilder();
        var model = context.neutralMetamodel();
        var ecore = context.targets().ecore();
        result.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        result.append("<metamodel fileName=\"").append(MetamodelExportSupport.xml(ecore.fileName())).append("\" schemaVersion=\"").append(MetamodelExportSupport.xml(model.schemaVersion())).append("\" id=\"").append(MetamodelExportSupport.xml(model.id())).append("\" name=\"").append(MetamodelExportSupport.xml(model.name())).append("\" namespaceUri=\"").append(MetamodelExportSupport.xml(ecore.namespaceUri())).append("\" namespacePrefix=\"").append(MetamodelExportSupport.xml(ecore.namespacePrefix())).append("\">\n");
        result.append("  <dataTypes>\n");

        for(MetaDataType dataType : model.dataTypes()) {
            var configuration = ecore.dataType(dataType.id());
            result.append("    <dataType id=\"").append(MetamodelExportSupport.xml(dataType.id())).append("\" name=\"").append(MetamodelExportSupport.xml(dataType.name())).append("\" implementationTypeName=\"").append(MetamodelExportSupport.xml(configuration.instanceClassName())).append("\" serializable=\"").append(configuration.serializable()).append("\"/>\n");
        }

        result.append("  </dataTypes>\n");
        result.append("  <enumerations>\n");

        for(MetaEnum enumDesign : model.enums()) {
            result.append("    <enumeration id=\"").append(MetamodelExportSupport.xml(enumDesign.id())).append("\" name=\"").append(MetamodelExportSupport.xml(enumDesign.name())).append("\">\n");

            for(MetaEnumLiteral literal : enumDesign.literals()) {
                result.append("      <literal id=\"").append(MetamodelExportSupport.xml(literal.id())).append("\" name=\"").append(MetamodelExportSupport.xml(literal.name())).append("\" value=\"").append(literal.value()).append("\"/>\n");
            }

            result.append("    </enumeration>\n");
        }

        result.append("  </enumerations>\n");
        result.append("  <classes>\n");

        for(MetaClass metaClass : model.classes()) {
            result.append("    <class id=\"").append(MetamodelExportSupport.xml(metaClass.id())).append("\" name=\"").append(MetamodelExportSupport.xml(metaClass.name())).append("\" kind=\"").append(metaClass.kind()).append("\">\n");

            for(MetaAttribute attribute : metaClass.attributes()) {
                result.append("      <attribute id=\"").append(MetamodelExportSupport.xml(attribute.id())).append("\" name=\"").append(MetamodelExportSupport.xml(attribute.name())).append("\" type=\"").append(MetamodelExportSupport.xml(MetamodelExportSupport.resolveTypeName(context, attribute.type()))).append("\" typeKind=\"").append(attribute.type().kind()).append("\" lowerBound=\"").append(attribute.multiplicity().lowerBound()).append("\" upperBound=\"").append(attribute.multiplicity().unbounded() ? "*" : attribute.multiplicity().upperBound()).append("\" ordered=\"").append(attribute.ordered()).append("\" unique=\"").append(attribute.unique()).append("\" changeable=\"").append(ecore.attribute(attribute.id()).changeable()).append("\" defaultValue=\"").append(MetamodelExportSupport.xml(attribute.defaultValue())).append("\"/>\n");
            }

            for(MetaOperation operation : metaClass.operations()) {
                result.append("      <operation id=\"").append(MetamodelExportSupport.xml(operation.id())).append("\" name=\"").append(MetamodelExportSupport.xml(operation.name())).append("\" behaviorKind=\"").append(operation.behaviorKind()).append("\" ordered=\"").append(operation.ordered()).append("\" unique=\"").append(operation.unique()).append("\"");
                if(operation.returnType() != null) {
                    appendTypeAttributes(result, context, "return", operation.returnType());
                    result.append(" returnLowerBound=\"").append(operation.returnMultiplicity().lowerBound()).append("\" returnUpperBound=\"").append(operation.returnMultiplicity().unbounded() ? "*" : operation.returnMultiplicity().upperBound()).append("\"");
                }
                result.append(">\n");
                for(MetaParameter parameter : operation.parameters()) {
                    result.append("        <parameter id=\"").append(MetamodelExportSupport.xml(parameter.id())).append("\" name=\"").append(MetamodelExportSupport.xml(parameter.name())).append("\"");
                    appendTypeAttributes(result, context, "", parameter.type());
                    result.append(" lowerBound=\"").append(parameter.multiplicity().lowerBound()).append("\" upperBound=\"").append(parameter.multiplicity().unbounded() ? "*" : parameter.multiplicity().upperBound()).append("\" ordered=\"").append(parameter.ordered()).append("\" unique=\"").append(parameter.unique()).append("\"/>\n");
                }
                for(var exceptionType : operation.exceptionTypes()) {
                    result.append("        <exception");
                    appendTypeAttributes(result, context, "", exceptionType);
                    result.append("/>\n");
                }
                result.append("      </operation>\n");
            }

            result.append("    </class>\n");
        }

        result.append("  </classes>\n");
        result.append("  <relations>\n");

        for(MetaRelation relation : model.relations()) {
            String elementName = MetamodelExportSupport.isStructural(relation) ? "reference" : "relation";
            result.append("    <").append(elementName).append(" id=\"").append(MetamodelExportSupport.xml(relation.id())).append("\" name=\"").append(MetamodelExportSupport.xml(relation.name())).append("\" sourceTypeId=\"").append(MetamodelExportSupport.xml(relation.sourceTypeId())).append("\" targetTypeId=\"").append(MetamodelExportSupport.xml(relation.targetTypeId())).append("\" kind=\"").append(relation.kind()).append("\" targetMultiplicity=\"").append(MetamodelExportSupport.multiplicity(relation.targetMultiplicity())).append("\" oppositeRelationId=\"").append(MetamodelExportSupport.xml(relation.oppositeRelationId())).append("\"/>\n");
        }

        result.append("  </relations>\n");

        if(context.options().includeLayout()) {
            result.append("  <diagram metamodelId=\"").append(MetamodelExportSupport.xml(context.diagram().metamodelId())).append("\" nodes=\"").append(context.diagram().nodes().size()).append("\" edges=\"").append(context.diagram().edges().size()).append("\"/>\n");
        }

        if(context.options().includeWarnings() && !context.warnings().isEmpty()) {
            result.append("  <warnings>\n");

            for(var warning : context.warnings()) {
                result.append("    <warning code=\"").append(MetamodelExportSupport.xml(warning.code())).append("\" path=\"").append(MetamodelExportSupport.xml(warning.path())).append("\">").append(MetamodelExportSupport.xml(warning.message())).append("</warning>\n");
            }

            result.append("  </warnings>\n");
        }

        result.append("</metamodel>\n");
        return result.toString();
    }
    /** Descripción: Ejecuta la operación «appendTypeAttributes» correspondiente a la responsabilidad de NeutralXmlMetamodelExporter. Entradas: result: dato de entrada necesario para la operación; context: dato de entrada necesario para la operación; prefix: dato de entrada necesario para la operación; type: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void appendTypeAttributes(StringBuilder result, NeutralMetamodelExportContext context, String prefix, com.example.editor.metamodel.core.MetaTypeReference type) {
        String displayAttribute = prefix.isBlank() ? "type" : prefix + "Type";
        String kindAttribute = prefix.isBlank() ? "typeKind" : prefix + "TypeKind";
        String idAttribute = prefix.isBlank() ? "typeId" : prefix + "TypeId";
        String externalAttribute = prefix.isBlank() ? "externalName" : prefix + "ExternalName";
        result.append(" ").append(displayAttribute).append("=\"").append(MetamodelExportSupport.xml(MetamodelExportSupport.resolveTypeName(context, type))).append("\"")
                .append(" ").append(kindAttribute).append("=\"").append(type.kind()).append("\"")
                .append(" ").append(idAttribute).append("=\"").append(MetamodelExportSupport.xml(type.targetId())).append("\"")
                .append(" ").append(externalAttribute).append("=\"").append(MetamodelExportSupport.xml(type.externalName())).append("\"");
    }

}
