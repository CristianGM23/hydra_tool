package com.example.editor.metamodel.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Traduce reglas técnicas a explicaciones accionables de alto nivel. */
@Component
public class MetamodelValidationAdvisor {
    private static final Pattern ELEMENT = Pattern.compile("\\[([^\\]]+)]");

    /** Descripción: Ejecuta la operación «explain» correspondiente a la responsabilidad de MetamodelValidationAdvisor. Entradas: issue: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelValidationDiagnostic explain(ValidationIssue issue){
        Advice advice = advice(issue.code());
        boolean blocking = issue.severity() == ValidationSeverity.ERROR;
        return new MetamodelValidationDiagnostic(issue.severity(), issue.code(), advice.category(), advice.title(),
                issue.message(), location(issue.path()), blocking
                    ? "The canonical structure is inconsistent. HYDRA can keep it open for correction, but derived project generation is blocked."
                    : "The metamodel is structurally usable, but this point can require a target-specific mapping or adaptation.",
                elements(issue.code(), issue.path(), issue.message()), advice.fixes(), blocking ? "Blocks complete EMF/Xtext/Langium/GLSP/BESSER project generation." : "Generation remains available unless warnings are configured as blocking.", issue.path());
    }

    /** Descripción: Ejecuta la operación «advice» correspondiente a la responsabilidad de MetamodelValidationAdvisor. Entradas: code: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Advice advice(String code){return switch(code){
        case "MODEL_NULL" -> a("Metamodel", "Metamodel document is missing", "Restore or recreate the canonical metamodel document.");
        case "MODEL_ID_EMPTY" -> a("Identity", "Metamodel identifier is missing", "Assign a stable technical identifier before saving.");
        case "MODEL_NAME_EMPTY" -> a("Identity", "Metamodel name is missing", "Give the metamodel a meaningful visible name.");
        case "MODEL_SCHEMA_VERSION_UNSUPPORTED" -> a("Versioning", "Metamodel schema is unsupported", "Import or migrate the document to the current MetaModel schema before continuing.");
        case "CLASSIFIER_ID_EMPTY" -> a("Classifier", "Classifier identifier is missing", "Assign a stable identifier to every class, interface, enumeration and datatype.");
        case "CLASSIFIER_NAME_EMPTY" -> a("Classifier", "Classifier name is missing", "Name the affected classifier.");
        case "CLASSIFIER_ID_DUPLICATE" -> a("Classifier", "Classifier identifier is duplicated", "Rename one of the classifier identifiers and update references to it.");
        case "CLASSIFIER_NAME_DUPLICATE" -> a("Classifier", "Classifier name is duplicated", "Use unique classifier names to avoid ambiguous generated types.");
        case "ATTRIBUTE_ID_EMPTY" -> a("Attribute", "Attribute identifier is missing", "Assign a stable identifier to the attribute.");
        case "ATTRIBUTE_NAME_EMPTY" -> a("Attribute", "Attribute name is missing", "Name the affected attribute.");
        case "ATTRIBUTE_ID_DUPLICATE" -> a("Attribute", "Attribute identifier is duplicated", "Use a unique attribute identifier within its owning class.");
        case "ATTRIBUTE_NAME_DUPLICATE" -> a("Attribute", "Attribute name is duplicated", "Use a unique attribute name within its owning class.");
        case "ATTRIBUTE_TYPE_EMPTY" -> a("Attribute", "Attribute type is missing", "Select a primitive, enumeration, datatype or explicit external type.");
        case "ATTRIBUTE_ENUM_UNKNOWN" -> a("Attribute", "Attribute references an unknown enumeration", "Select an existing enumeration or recreate the referenced enumeration.");
        case "ATTRIBUTE_DATATYPE_UNKNOWN" -> a("Attribute", "Attribute references an unknown datatype", "Select an existing datatype or recreate the referenced datatype.");
        case "ATTRIBUTE_CLASS_TYPE" -> a("Attribute", "A class is used as an attribute type", "Replace the attribute with a relation to the target class.");
        case "ATTRIBUTE_EXTERNAL_TYPE" -> a("Target mapping", "Attribute uses an external type", "Define the external type mapping for each target technology that will receive this metamodel.");
        case "MULTIPLICITY_EMPTY" -> a("Multiplicity", "Multiplicity is incomplete", "Set lower and upper bounds explicitly.");
        case "MULTIPLICITY_LOWER_INVALID" -> a("Multiplicity", "Lower multiplicity is invalid", "Use a non-negative lower bound that does not exceed the upper bound.");
        case "MULTIPLICITY_UPPER_INVALID" -> a("Multiplicity", "Upper multiplicity is invalid", "Use -1/unbounded or an upper bound greater than or equal to the lower bound.");
        case "ENUM_EMPTY" -> a("Enumeration", "Enumeration has no literals", "Add at least one literal to the enumeration.");
        case "ENUM_LITERAL_ID_DUPLICATE" -> a("Enumeration", "Enumeration literal identifier is duplicated", "Use unique literal identifiers inside the enumeration.");
        case "ENUM_LITERAL_NAME_DUPLICATE" -> a("Enumeration", "Enumeration literal name is duplicated", "Use unique literal names inside the enumeration.");
        case "ENUM_LITERAL_VALUE_DUPLICATE" -> a("Enumeration", "Enumeration literal value is duplicated", "Assign distinct numeric values when the target requires stable enum values.");
        case "RELATION_ID_EMPTY" -> a("Relation", "Relation identifier is missing", "Assign a stable identifier to the relation.");
        case "RELATION_ID_DUPLICATE" -> a("Relation", "Relation identifier is duplicated", "Use a unique relation identifier.");
        case "RELATION_SOURCE_UNKNOWN" -> a("Relation", "Relation source does not exist", "Reconnect the relation to an existing classifier or recreate the missing source.");
        case "RELATION_TARGET_UNKNOWN" -> a("Relation", "Relation target does not exist", "Reconnect the relation to an existing classifier or recreate the missing target.");
        case "RELATION_OPPOSITE_UNKNOWN" -> a("Relation", "Opposite relation cannot be resolved", "Select an existing reverse relation or remove the opposite link.");
        case "RELATION_OPPOSITE_INCOMPATIBLE" -> a("Relation", "Opposite relations are inconsistent", "Make both relations point to each other and ensure their source/target types are reversed consistently.");
        case "STRUCTURAL_RELATION_TO_INTERFACE" -> a("Relation", "Structural relation targets an interface", "Use a concrete class for containment/reference structure, or represent the interface relation as implementation/dependency where appropriate.");
        case "INHERITANCE_INVALID" -> a("Inheritance", "Inheritance endpoints are invalid", "Connect inheritance only between compatible class kinds.");
        case "IMPLEMENTATION_INVALID" -> a("Implementation", "Interface implementation endpoints are invalid", "Connect implementation from a class to an interface.");
        case "INHERITANCE_CYCLE" -> a("Inheritance", "Inheritance cycle detected", "Break the cycle so no classifier eventually inherits from itself.");
        case "OPERATION_ID_EMPTY" -> a("Operation", "Operation identifier is missing", "Assign a stable identifier to the operation.");
        case "OPERATION_NAME_EMPTY" -> a("Operation", "Operation name is missing", "Name the affected operation.");
        case "OPERATION_ID_DUPLICATE" -> a("Operation", "Operation identifier is duplicated", "Use a globally unique operation identifier.");
        case "OPERATION_SIGNATURE_DUPLICATE" -> a("Operation", "Operation signature is duplicated", "Rename the operation or change its parameter signature.");
        case "OPERATION_VOID_MULTIPLICITY" -> a("Operation", "Void operation declares result multiplicity", "Remove the return multiplicity or define a return type.");
        case "OPERATION_RETURN" -> a("Operation", "Operation return type cannot be resolved", "Choose an existing classifier/datatype/enum or a supported primitive/external type.");
        case "OPERATION_EXCEPTION" -> a("Operation", "Operation exception type cannot be resolved", "Use a supported exception type or define an explicit external target mapping.");
        case "PARAMETER_ID_EMPTY" -> a("Operation parameter", "Parameter identifier is missing", "Assign a stable identifier to the parameter.");
        case "PARAMETER_NAME_EMPTY" -> a("Operation parameter", "Parameter name is missing", "Name the parameter.");
        case "PARAMETER_ID_DUPLICATE" -> a("Operation parameter", "Parameter identifier is duplicated", "Use a unique parameter identifier within the operation.");
        case "PARAMETER_NAME_DUPLICATE" -> a("Operation parameter", "Parameter name is duplicated", "Use a unique parameter name within the operation.");
        case "PARAMETER" -> a("Operation parameter", "Parameter type cannot be resolved", "Choose a supported parameter type and verify the referenced classifier exists.");
        default -> a("Structure", "Metamodel constraint is not satisfied", "Review the affected element and correct the value described by the validation message.");
    };}

    /** Descripción: Ejecuta la operación «a» correspondiente a la responsabilidad de MetamodelValidationAdvisor. Entradas: category: dato de entrada necesario para la operación; title: dato de entrada necesario para la operación; fixes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Advice a(String category, String title, String... fixes){return new Advice(category, title, List.of(fixes));}
    /** Descripción: Ejecuta la operación «location» correspondiente a la responsabilidad de MetamodelValidationAdvisor. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String location(String path){
        if(path == null || path.isBlank() || path.equals("$")){return "Metamodel root";}
        return path.replace("$.", "").replace(".classes[", " → class [").replace("classes[", "class [")
                .replace(".attributes[", " → attribute [").replace(".operations[", " → operation [")
                .replace(".parameters[", " → parameter [").replace(".relations[", " → relation [")
                .replace(".enums[", " → enumeration [").replace(".dataTypes[", " → datatype [");
    }
    /** Descripción: Ejecuta la operación «elements» correspondiente a la responsabilidad de MetamodelValidationAdvisor. Entradas: code: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> elements(String code, String path, String message){
        List<String> result = new ArrayList<>();
        if(path != null){
            Matcher matcher = ELEMENT.matcher(path);
            while(matcher.find()){addElement(result, matcher.group(1));}
        }
        addMessageElement(result, code, message);
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «addMessageElement» correspondiente a la responsabilidad de MetamodelValidationAdvisor. Entradas: result: dato de entrada necesario para la operación; code: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addMessageElement(List<String> result, String code, String message){
        if(message == null || message.isBlank()){return;}
        int separator = message.lastIndexOf(':');
        if(separator < 0 || separator + 1 >= message.length()){return;}
        String value = message.substring(separator + 1).trim();
        if(value.length() <= 160 && (!value.contains(" ") || value.matches("[A-Za-z0-9_.:/#-]+"))){
            addElement(result, normalizeMessageElement(code, value));
        }
    }

    /** Descripción: Ejecuta la operación «normalizeMessageElement» correspondiente a la responsabilidad de MetamodelValidationAdvisor. Entradas: code: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalizeMessageElement(String code, String value){
        if("RELATION_OPPOSITE_UNKNOWN".equals(code) && !value.startsWith("relation:")){
            return "relation:" + value;
        }
        return value;
    }

    /** Descripción: Ejecuta la operación «addElement» correspondiente a la responsabilidad de MetamodelValidationAdvisor. Entradas: result: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addElement(List<String> result, String value){
        if(value == null){return;}
        String normalized = value.trim();
        if(!normalized.isBlank() && !result.contains(normalized)){result.add(normalized);}
    }
    private record Advice(String category, String title, List<String> fixes){}
}
