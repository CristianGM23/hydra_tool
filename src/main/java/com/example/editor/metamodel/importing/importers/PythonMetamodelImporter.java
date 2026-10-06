package com.example.editor.metamodel.importing.importers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.editor.metamodel.core.MetaAttribute;
import com.example.editor.metamodel.core.MetaClass;
import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaOperationBehaviorKind;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.MetaPrimitiveType;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.importing.ImportSourceEntry;
import com.example.editor.metamodel.importing.MetamodelImportFormat;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportOutput;
import com.example.editor.metamodel.importing.MetamodelImportSource;
import com.example.editor.metamodel.importing.MetamodelImporter;
import com.example.editor.metamodel.importing.SafeImportArchiveReader;
import com.example.editor.metamodel.importing.python.PythonAstAnalyzer;
import com.example.editor.metamodel.importing.python.PythonFieldSpec;
import com.example.editor.metamodel.importing.python.PythonMethodSpec;
import com.example.editor.metamodel.importing.python.PythonParameterSpec;
import com.example.editor.metamodel.importing.python.PythonProjectAnalysis;
import com.example.editor.metamodel.importing.python.PythonTypeSpec;
import com.example.editor.metamodel.importing.support.ImportParsingSupport;

/** Importa modelos declarativos Python mediante AST sin importar ni ejecutar módulos. */
@Component
public class PythonMetamodelImporter implements MetamodelImporter {

    private static final Set<String> SOURCE_EXTENSIONS = Set.of(".py");
    private final SafeImportArchiveReader archiveReader;
    private final PythonAstAnalyzer analyzer;

    /** Descripción: Inicializa una instancia de PythonMetamodelImporter con sus dependencias y datos de entrada. Entradas: archiveReader: archivo o contenido que debe procesarse; analyzer: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public PythonMetamodelImporter(SafeImportArchiveReader archiveReader, PythonAstAnalyzer analyzer) {
        this.archiveReader = archiveReader;
        this.analyzer = analyzer;
    }

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de PythonTypeUse. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportFormat format() {
        return MetamodelImportFormat.PYTHON;
    }

    @Override
    /** Descripción: Ejecuta la operación «importMetamodel» correspondiente a la responsabilidad de PythonTypeUse. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportOutput importMetamodel(MetamodelImportSource source, MetamodelImportOptions options) {
        List<ImportSourceEntry> sources = archiveReader.read(source, SOURCE_EXTENSIONS);
        PythonProjectAnalysis project = analyzer.analyze(sources);
        PythonModelBuilder builder = new PythonModelBuilder(source.fileName(), project);
        return new MetamodelImportOutput(builder.build(), null, builder.warnings());
    }

    private static final class PythonModelBuilder {
        private static final Set<String> ENUM_BASES = Set.of("Enum", "IntEnum", "StrEnum", "Flag", "IntFlag");
        private static final Set<String> ABSTRACT_BASES = Set.of("ABC", "ABCMeta");
        private static final Set<String> INTERFACE_DECORATORS = Set.of("runtime_checkable", "interface");
        private final String sourceName;
        private final PythonProjectAnalysis project;
        private final List<TransformationWarning> warnings = new ArrayList<>();
        private final Map<String, PythonTypeSpec> typesByQualified = new LinkedHashMap<>();
        private final Map<String, String> uniqueQualifiedBySimple = new LinkedHashMap<>();
        private final Map<String, String> idsByQualified = new LinkedHashMap<>();
        private final Set<String> structuralInterfaceTargets = new LinkedHashSet<>();

        /** Descripción: Inicializa una instancia de PythonModelBuilder con sus dependencias y datos de entrada. Entradas: sourceName: dato de entrada necesario para la operación; project: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private PythonModelBuilder(String sourceName, PythonProjectAnalysis project) {
            this.sourceName = sourceName;
            this.project = project;
            warnings.addAll(project.diagnostics().stream().map(message -> new TransformationWarning("IMPORT_PYTHON_ANALYZER_FALLBACK", "$", message)).toList());
            indexTypes();
            indexStructuralInterfaceTargets();
        }

        /** Descripción: Crea o prepara el resultado de «build» dentro de PythonTypeUse. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel build() {
            List<MetaClass> classes = new ArrayList<>();
            List<MetaEnum> enums = new ArrayList<>();
            List<MetaRelation> relations = new ArrayList<>();
            for(PythonTypeSpec type : project.types()) {
                if(isEnum(type)) enums.add(buildEnum(type));
                else classes.add(buildClass(type, relations));
            }
            String base = ImportParsingSupport.fileBaseName(sourceName);
            Map<String, String> annotations = new LinkedHashMap<>();
            annotations.put("import:format", "python");
            annotations.put("import:originKind", "IMPORTED");
            annotations.put("import:source", sourceName);
            annotations.put("import:sourceFiles", String.valueOf(project.types().stream().map(PythonTypeSpec::sourcePath).distinct().count()));
            annotations.put("import:modules", String.join(",", project.modules()));
            annotations.put("import:pythonAnalyzer", project.fallbackUsed() ? "CONSERVATIVE_FALLBACK" : "AST");
            annotations.put("roundTrip:markerStart", "# <hydra-generated id=\"...\">");
            annotations.put("roundTrip:markerEnd", "# </hydra-generated>");
            annotations.put("roundTrip:preserveManualRegions", "true");
            return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, ImportParsingSupport.safeId(base, "python-model"), ImportParsingSupport.pascal(base, "PythonModel"), classes, enums, List.of(), relations, annotations);
        }

        /** Descripción: Ejecuta la operación «indexTypes» correspondiente a la responsabilidad de PythonTypeUse. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void indexTypes() {
            Map<String, Integer> counts = new HashMap<>();
            for(PythonTypeSpec type : project.types()) counts.merge(type.name(), 1, Integer::sum);
            for(PythonTypeSpec type : project.types()) {
                String qualified = type.qualifiedName().isBlank() ? type.name() : type.qualifiedName();
                typesByQualified.put(qualified, type);
                idsByQualified.put(qualified, ImportParsingSupport.safeId(qualified, "type"));
                if(counts.getOrDefault(type.name(), 0) == 1) uniqueQualifiedBySimple.put(type.name(), qualified);
            }
        }

        /** Descripción: Ejecuta la operación «indexStructuralInterfaceTargets» correspondiente a la responsabilidad de PythonTypeUse. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void indexStructuralInterfaceTargets() {
            for(PythonTypeSpec owner : project.types()) {
                for(PythonFieldSpec field : owner.fields()) {
                    PythonTypeUse use = parseType(field.type());
                    String target = resolveKnownType(use.baseType(), owner);
                    if(!target.isBlank() && isInterface(typesByQualified.get(target))) structuralInterfaceTargets.add(target);
                }
            }
        }

        /** Descripción: Ejecuta la operación «effectiveKind» correspondiente a la responsabilidad de PythonTypeUse. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClassKind effectiveKind(PythonTypeSpec type) {
            if(isInterface(type)) return structuralInterfaceTargets.contains(qualified(type)) ? MetaClassKind.ABSTRACT_CLASS : MetaClassKind.INTERFACE;
            return isAbstract(type) ? MetaClassKind.ABSTRACT_CLASS : MetaClassKind.CLASS;
        }

        /** Descripción: Crea o prepara el resultado de «buildEnum» dentro de PythonTypeUse. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaEnum buildEnum(PythonTypeSpec type) {
            String qualified = qualified(type);
            String id = idsByQualified.get(qualified);
            List<MetaEnumLiteral> literals = new ArrayList<>();
            for(int index = 0; index < type.enumLiterals().size(); index++) {
                String literal = type.enumLiterals().get(index);
                literals.add(new MetaEnumLiteral(ImportParsingSupport.literalId(id, literal), literal, index, Map.of("python:qualifiedName", qualified + "." + literal)));
            }
            return new MetaEnum(id, type.name(), literals, annotations(type));
        }

        /** Descripción: Crea o prepara el resultado de «buildClass» dentro de PythonTypeUse. Entradas: type: dato de entrada necesario para la operación; relations: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClass buildClass(PythonTypeSpec type, List<MetaRelation> relations) {
            String qualified = qualified(type);
            String classId = idsByQualified.get(qualified);
            List<MetaAttribute> attributes = new ArrayList<>();
            List<MetaOperation> operations = new ArrayList<>();
            for(PythonFieldSpec field : type.fields()) {
                PythonTypeUse use = parseType(field.type());
                String target = resolveKnownType(use.baseType(), type);
                Map<String, String> annotations = fieldAnnotations(type, field);
                annotations.put("python:genericType", field.type());
                if(!target.isBlank() && !isEnum(typesByQualified.get(target))) {
                    relations.add(new MetaRelation(ImportParsingSupport.relationId(classId, field.name()), field.name(), classId, idsByQualified.get(target), relationKind(field), null, use.multiplicity(), "", use.ordered(), use.unique(), annotations));
                } else {
                    attributes.add(new MetaAttribute(ImportParsingSupport.attributeId(classId, field.name()), field.name(), typeReference(use.baseType(), target), use.multiplicity(), use.ordered(), use.unique(), normalizeDefault(field.defaultValue()), annotations));
                }
            }
            for(PythonMethodSpec method : type.methods()) {
                if("__init__".equals(method.name()) || "__new__".equals(method.name())) continue;
                operations.add(buildOperation(classId, type, method));
            }
            for(String superType : type.bases()) {
                PythonTypeUse use = parseType(superType);
                String target = resolveKnownType(use.baseType(), type);
                if(target.isBlank()) {
                    if(!isFrameworkBase(use.baseType())) warnings.add(new TransformationWarning("IMPORT_PYTHON_EXTERNAL_SUPERTYPE", "$.classes[" + classId + "]", "External Python base was preserved as provenance: " + superType));
                    continue;
                }
                PythonTypeSpec parent = typesByQualified.get(target);
                MetaRelationKind kind = effectiveKind(parent) == MetaClassKind.INTERFACE && effectiveKind(type) != MetaClassKind.INTERFACE ? MetaRelationKind.IMPLEMENTATION : MetaRelationKind.INHERITANCE;
                relations.add(new MetaRelation((kind == MetaRelationKind.IMPLEMENTATION ? "implementation:" : "supertype:") + classId + ":" + idsByQualified.get(target), "", classId, idsByQualified.get(target), kind, null, null, "", true, true, Map.of("python:sourceType", superType)));
            }
            MetaClassKind kind = effectiveKind(type);
            Map<String, String> classAnnotations = annotations(type);
            if(isInterface(type) && kind != MetaClassKind.INTERFACE) {
                classAnnotations.put("python:declaredInterface", "true");
                classAnnotations.put("hydra:semanticAdaptation", "STRUCTURAL_INTERFACE_TO_ABSTRACT_CLASS");
                warnings.add(new TransformationWarning("IMPORT_PYTHON_STRUCTURAL_INTERFACE_ADAPTED", "$.classes[" + classId + "]", "Python Protocol was represented as an abstract class because HYDRA structural relations cannot target interfaces"));
            }
            return new MetaClass(classId, type.name(), kind, attributes, operations, classAnnotations);
        }

        /** Descripción: Ejecuta la operación «relationKind» correspondiente a la responsabilidad de PythonTypeUse. Entradas: field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaRelationKind relationKind(PythonFieldSpec field) {
            String decorators = String.join(" ", field.decorators()).toLowerCase(Locale.ROOT);
            String type = field.type().toLowerCase(Locale.ROOT);
            if(decorators.contains("contain") || decorators.contains("composition") || type.contains("containment")) return MetaRelationKind.CONTAINMENT;
            if(decorators.contains("aggregate")) return MetaRelationKind.AGGREGATION;
            return MetaRelationKind.REFERENCE;
        }

        /** Descripción: Crea o prepara el resultado de «buildOperation» dentro de PythonTypeUse. Entradas: classId: identificador que delimita el recurso objetivo; owner: dato de entrada necesario para la operación; method: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaOperation buildOperation(String classId, PythonTypeSpec owner, PythonMethodSpec method) {
            String operationId = "operation:" + classId + ":" + ImportParsingSupport.safeId(method.name(), "operation");
            List<MetaParameter> parameters = new ArrayList<>();
            for(PythonParameterSpec parameter : method.parameters()) {
                PythonTypeUse use = parseType(parameter.type());
                String target = resolveKnownType(use.baseType(), owner);
                Map<String, String> annotations = new LinkedHashMap<>();
                annotations.put("python:genericType", parameter.type());
                annotations.put("python:defaultValue", normalizeDefault(parameter.defaultValue()));
                parameters.add(new MetaParameter("parameter:" + operationId + ":" + ImportParsingSupport.safeId(parameter.name(), "parameter"), parameter.name(), typeReference(use.baseType(), target), use.multiplicity(), use.ordered(), use.unique(), annotations));
            }
            PythonTypeUse returnUse = parseType(method.returnType());
            boolean voidType = Set.of("None", "NoneType", "NoReturn", "Never", "").contains(returnUse.baseType());
            String target = voidType ? "" : resolveKnownType(returnUse.baseType(), owner);
            Map<String, String> annotations = new LinkedHashMap<>();
            annotations.put("python:decorators", String.join(",", method.decorators()));
            annotations.put("python:genericReturnType", method.returnType());
            annotations.put("python:async", String.valueOf(method.asynchronous()));
            annotations.put("python:bodyImported", "false");
            if(method.hasBody()) warnings.add(new TransformationWarning("IMPORT_PYTHON_METHOD_BODY_OMITTED", "$.classes[" + classId + "].operations[" + operationId + "]", "Python method bodies are not executed or converted automatically"));
            return new MetaOperation(operationId, method.name(), voidType ? null : typeReference(returnUse.baseType(), target), voidType ? null : returnUse.multiplicity(), returnUse.ordered(), returnUse.unique(), parameters, List.of(), MetaOperationBehaviorKind.UNSPECIFIED, annotations);
        }

        /** Descripción: Ejecuta la operación «parseType» correspondiente a la responsabilidad de PythonTypeUse. Entradas: raw: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private PythonTypeUse parseType(String raw) {
            String value = raw == null || raw.isBlank() ? "Any" : raw.trim();
            value = stripForwardReference(value);
            if(value.contains(" | ") || value.contains("|")) {
                List<String> alternatives = splitTopLevel(value, '|');
                boolean optional = alternatives.stream().anyMatch(item -> item.trim().equals("None"));
                List<String> concrete = alternatives.stream().map(String::trim).filter(item -> !item.equals("None")).toList();
                return new PythonTypeUse(concrete.size() == 1 ? concrete.getFirst() : "Any", optional ? Multiplicity.optional() : Multiplicity.one(), false, true, true);
            }
            TypeExpression expression = typeExpression(value);
            String container = simpleName(expression.name());
            List<String> arguments = expression.arguments();
            if(Set.of("Optional").contains(container) && !arguments.isEmpty()) return new PythonTypeUse(arguments.getFirst(), Multiplicity.optional(), false, true, true);
            if(Set.of("Union").contains(container) && !arguments.isEmpty()) {
                List<String> concrete = arguments.stream().filter(item -> !item.equals("None")).toList();
                return new PythonTypeUse(concrete.size() == 1 ? concrete.getFirst() : "Any", arguments.size() != concrete.size() ? Multiplicity.optional() : Multiplicity.one(), false, true, true);
            }
            if(Set.of("list", "List", "Sequence", "MutableSequence", "Iterable", "Collection", "deque", "tuple", "Tuple").contains(container)) return new PythonTypeUse(arguments.isEmpty() ? "Any" : arguments.getFirst(), Multiplicity.many(), true, true, false);
            if(Set.of("set", "Set", "frozenset", "FrozenSet").contains(container)) return new PythonTypeUse(arguments.isEmpty() ? "Any" : arguments.getFirst(), Multiplicity.many(), true, false, true);
            if(Set.of("dict", "Dict", "Mapping", "MutableMapping").contains(container)) return new PythonTypeUse("Any", Multiplicity.many(), true, false, true);
            if(Set.of("ClassVar", "Final", "Annotated", "Required", "NotRequired").contains(container) && !arguments.isEmpty()) return parseType(arguments.getFirst());
            return new PythonTypeUse(value, Multiplicity.one(), false, true, true);
        }

        /** Descripción: Ejecuta la operación «typeReference» correspondiente a la responsabilidad de PythonTypeUse. Entradas: baseType: dato de entrada necesario para la operación; knownQualified: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaTypeReference typeReference(String baseType, String knownQualified) {
            if(!knownQualified.isBlank()) {
                PythonTypeSpec known = typesByQualified.get(knownQualified);
                return isEnum(known) ? MetaTypeReference.enumType(idsByQualified.get(knownQualified)) : MetaTypeReference.classType(idsByQualified.get(knownQualified));
            }
            String normalized = simpleName(stripForwardReference(baseType));
            return switch(normalized) {
                case "str", "String", "UUID", "Path" -> MetaTypeReference.primitive(MetaPrimitiveType.STRING);
                case "bool" -> MetaTypeReference.primitive(MetaPrimitiveType.BOOLEAN);
                case "int" -> MetaTypeReference.primitive(MetaPrimitiveType.LONG);
                case "float" -> MetaTypeReference.primitive(MetaPrimitiveType.DOUBLE);
                case "Decimal" -> MetaTypeReference.primitive(MetaPrimitiveType.DECIMAL);
                case "bytes", "bytearray", "memoryview" -> MetaTypeReference.primitive(MetaPrimitiveType.BINARY);
                case "date", "datetime", "time", "timedelta" -> MetaTypeReference.primitive(MetaPrimitiveType.DATE);
                case "Any", "object", "None", "NoneType" -> MetaTypeReference.primitive(MetaPrimitiveType.OBJECT);
                default -> MetaTypeReference.external(baseType);
            };
        }

        /** Descripción: Obtiene la información correspondiente a «resolveKnownType» dentro de PythonTypeUse. Entradas: baseType: dato de entrada necesario para la operación; owner: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String resolveKnownType(String baseType, PythonTypeSpec owner) {
            String clean = stripForwardReference(baseType).trim();
            if(typesByQualified.containsKey(clean)) return clean;
            String ownerQualified = qualified(owner);
            int nestedSeparator = ownerQualified.lastIndexOf('.');
            if(nestedSeparator > 0) {
                String sibling = ownerQualified.substring(0, nestedSeparator + 1) + clean;
                if(typesByQualified.containsKey(sibling)) return sibling;
            }
            String moduleQualified = owner.module().isBlank() ? clean : owner.module() + "." + clean;
            if(typesByQualified.containsKey(moduleQualified)) return moduleQualified;
            return uniqueQualifiedBySimple.getOrDefault(simpleName(clean), "");
        }

        /** Descripción: Ejecuta la operación «annotations» correspondiente a la responsabilidad de PythonTypeUse. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, String> annotations(PythonTypeSpec type) {
            Map<String, String> result = new LinkedHashMap<>();
            result.put("python:qualifiedName", qualified(type));
            result.put("python:module", type.module());
            result.put("python:sourcePath", type.sourcePath());
            result.put("python:bases", String.join(",", type.bases()));
            result.put("python:decorators", String.join(",", type.decorators()));
            if(isDeclaredProtocol(type)) result.put("python:declaredProtocol", "true");
            return result;
        }

        /** Descripción: Ejecuta la operación «fieldAnnotations» correspondiente a la responsabilidad de PythonTypeUse. Entradas: owner: dato de entrada necesario para la operación; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, String> fieldAnnotations(PythonTypeSpec owner, PythonFieldSpec field) {
            Map<String, String> result = new LinkedHashMap<>();
            result.put("python:qualifiedName", qualified(owner) + "." + field.name());
            result.put("python:decorators", String.join(",", field.decorators()));
            result.put("python:classVariable", String.valueOf(field.classVariable()));
            return result;
        }

        /** Descripción: Evalúa la condición «isEnum» sin modificar el estado externo. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isEnum(PythonTypeSpec type) {
            return type != null && (type.bases().stream().map(PythonModelBuilder::simpleName).anyMatch(ENUM_BASES::contains) || !type.enumLiterals().isEmpty());
        }

        /** Descripción: Evalúa la condición «isAbstract» sin modificar el estado externo. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isAbstract(PythonTypeSpec type) {
            return type.bases().stream().map(PythonModelBuilder::simpleName).anyMatch(ABSTRACT_BASES::contains)
                    || type.decorators().stream().map(PythonModelBuilder::simpleName).anyMatch(value -> value.equals("abstractclass"))
                    || type.methods().stream().flatMap(method -> method.decorators().stream()).map(PythonModelBuilder::simpleName).anyMatch(value -> value.equals("abstractmethod"));
        }

        /** Descripción: Evalúa la condición «isInterface» sin modificar el estado externo. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isInterface(PythonTypeSpec type) {
            if(type == null) return false;
            return isDeclaredProtocol(type)
                    || type.decorators().stream().map(PythonModelBuilder::simpleName).anyMatch(INTERFACE_DECORATORS::contains);
        }

        /** Descripción: Evalúa la condición «isDeclaredProtocol» sin modificar el estado externo. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isDeclaredProtocol(PythonTypeSpec type) {
            return type != null && type.bases().stream().map(PythonModelBuilder::simpleName).anyMatch(value -> value.equals("Protocol"));
        }

        /** Descripción: Evalúa la condición «isFrameworkBase» sin modificar el estado externo. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isFrameworkBase(String value) {
            return Set.of("ABC", "ABCMeta", "Protocol", "Generic", "Enum", "IntEnum", "StrEnum", "Flag", "IntFlag", "object").contains(simpleName(value));
        }

        /** Descripción: Ejecuta la operación «qualified» correspondiente a la responsabilidad de PythonTypeUse. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String qualified(PythonTypeSpec type) {
            return type.qualifiedName().isBlank() ? type.name() : type.qualifiedName();
        }

        /** Descripción: Ejecuta la operación «normalizeDefault» correspondiente a la responsabilidad de PythonTypeUse. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalizeDefault(String value) {
            if(value == null) return "";
            String normalized = value.trim();
            return "None".equals(normalized) ? "" : normalized;
        }

        /** Descripción: Ejecuta la operación «warnings» correspondiente a la responsabilidad de PythonTypeUse. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private List<TransformationWarning> warnings() {
            return List.copyOf(warnings);
        }

        /** Descripción: Ejecuta la operación «stripForwardReference» correspondiente a la responsabilidad de PythonTypeUse. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String stripForwardReference(String value) {
            String result = value == null ? "Any" : value.trim();
            if(result.length() >= 2 && ((result.startsWith("\"") && result.endsWith("\"")) || (result.startsWith("'") && result.endsWith("'")))) result = result.substring(1, result.length() - 1);
            return result;
        }

        /** Descripción: Ejecuta la operación «simpleName» correspondiente a la responsabilidad de PythonTypeUse. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String simpleName(String value) {
            String result = stripForwardReference(value);
            int generic = result.indexOf('[');
            if(generic >= 0) result = result.substring(0, generic);
            int dot = result.lastIndexOf('.');
            return dot >= 0 ? result.substring(dot + 1) : result;
        }

        /** Descripción: Ejecuta la operación «typeExpression» correspondiente a la responsabilidad de PythonTypeUse. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static TypeExpression typeExpression(String value) {
            int opening = value.indexOf('[');
            if(opening < 0 || !value.endsWith("]")) return new TypeExpression(value, List.of());
            return new TypeExpression(value.substring(0, opening).trim(), splitTopLevel(value.substring(opening + 1, value.length() - 1), ','));
        }

        /** Descripción: Ejecuta la operación «splitTopLevel» correspondiente a la responsabilidad de PythonTypeUse. Entradas: value: valor de entrada que debe procesarse; separator: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> splitTopLevel(String value, char separator) {
            List<String> result = new ArrayList<>();
            int depth = 0;
            int start = 0;
            char quote = 0;
            for(int index = 0; index < value.length(); index++) {
                char current = value.charAt(index);
                if(quote != 0) {
                    if(current == quote && (index == 0 || value.charAt(index - 1) != '\\')) quote = 0;
                    continue;
                }
                if(current == '\'' || current == '"') quote = current;
                else if(current == '[' || current == '(' || current == '{') depth++;
                else if(current == ']' || current == ')' || current == '}') depth--;
                else if(current == separator && depth == 0) {
                    result.add(value.substring(start, index).trim());
                    start = index + 1;
                }
            }
            result.add(value.substring(start).trim());
            return result.stream().filter(item -> !item.isBlank()).toList();
        }
    }

    private record PythonTypeUse(String baseType, Multiplicity multiplicity, boolean collection, boolean ordered, boolean unique) { }
    private record TypeExpression(String name, List<String> arguments) { }
}
