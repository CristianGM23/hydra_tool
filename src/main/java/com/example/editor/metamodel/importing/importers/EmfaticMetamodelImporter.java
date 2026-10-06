package com.example.editor.metamodel.importing.importers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import com.example.editor.metamodel.core.MetaDataType;
import com.example.editor.metamodel.core.MetaEnum;
import com.example.editor.metamodel.core.MetaEnumLiteral;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.MetaOperation;
import com.example.editor.metamodel.core.MetaOperationBehaviorKind;
import com.example.editor.metamodel.core.MetaParameter;
import com.example.editor.metamodel.core.MetaRelation;
import com.example.editor.metamodel.core.MetaRelationKind;
import com.example.editor.metamodel.core.MetaTypeReference;
import com.example.editor.metamodel.core.MetaTypeReferenceKind;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.core.TransformationWarning;
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;
import com.example.editor.metamodel.importing.MetamodelImportFormat;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportOutput;
import com.example.editor.metamodel.importing.MetamodelImportSource;
import com.example.editor.metamodel.importing.MetamodelImporter;
import com.example.editor.metamodel.importing.support.ImportParsingSupport;
import com.example.editor.metamodel.importing.canonicalization.EcoreImportMetadata;

/**
 * Importa la sintaxis textual Emfatic usada por EMF para describir metamodelos
 * Ecore. El parser es autocontenido para mantener el núcleo independiente del
 * IDE Eclipse y conserva como anotaciones las decisiones no neutrales.
 */
@Component
public class EmfaticMetamodelImporter implements MetamodelImporter {

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de EmfaticMetamodelImporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportFormat format() {
        return MetamodelImportFormat.EMF;
    }

    @Override
    /** Descripción: Ejecuta la operación «importMetamodel» correspondiente a la responsabilidad de EmfaticMetamodelImporter. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportOutput importMetamodel(MetamodelImportSource source, MetamodelImportOptions options) {
        String text = new String(source.content(), StandardCharsets.UTF_8);
        ParsedEmfatic parsed = new EmfaticSyntaxParser(text, source.fileName()).parse();
        BuildContext context = new BuildContext(parsed, source.fileName());
        return new MetamodelImportOutput(context.build(), null, context.warnings());
    }

    /** Descripción: Ejecuta la operación «parseSyntax» correspondiente a la responsabilidad de EmfaticMetamodelImporter. Entradas: text: valor de entrada que debe procesarse; sourceFile: archivo o contenido que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ static ParsedEmfatic parseSyntax(String text, String sourceFile) { return new EmfaticSyntaxEngine(text, sourceFile).parse(); }

    private static final class BuildContext {
        private final ParsedEmfatic parsed;
        private final String sourceFile;
        private final List<TransformationWarning> warnings;
        private final Map<String, String> classIds = new LinkedHashMap<>();
        private final Map<String, String> enumIds = new LinkedHashMap<>();
        private final Map<String, String> dataTypeIds = new LinkedHashMap<>();
        private final Map<String, String> uniqueSimpleClassIds = new LinkedHashMap<>();
        private final Map<String, String> uniqueSimpleEnumIds = new LinkedHashMap<>();
        private final Map<String, String> uniqueSimpleDataTypeIds = new LinkedHashMap<>();
        private final Map<String, MetaClass> externalClasses = new LinkedHashMap<>();

        /** Descripción: Inicializa una instancia de BuildContext con sus dependencias y datos de entrada. Entradas: parsed: dato de entrada necesario para la operación; sourceFile: archivo o contenido que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ private BuildContext(ParsedEmfatic parsed, String sourceFile) {
            this.parsed = parsed;
            this.sourceFile = sourceFile;
            this.warnings = new ArrayList<>(parsed.warnings);
            indexClassifiers();
        }

        /** Descripción: Crea o prepara el resultado de «build» dentro de EmfaticMetamodelImporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel build() {
            List<MetaEnum> enums = parsed.enums.stream().map(this::buildEnum).toList();
            List<MetaDataType> dataTypes = parsed.dataTypes.stream().map(this::buildDataType).toList();
            List<MetaClass> classes = new ArrayList<>();

            for(ClassSpec specification : parsed.classes) {
                classes.add(buildClass(specification));
            }

            List<MetaRelation> relations = buildRelations();
            classes.addAll(externalClasses.values());
            Map<String, String> annotations = new LinkedHashMap<>(parsed.annotations);
            String modelId = ImportParsingSupport.safeId(parsed.packageName, ImportParsingSupport.fileBaseName(sourceFile));
            annotations.put("emfatic:package", parsed.packageName);
            annotations.put("emfatic:source", sourceFile);
            annotations.put(EcoreImportMetadata.ARTIFACT_NAME, ImportParsingSupport.fileBaseName(sourceFile) + ".ecore");
            annotations.put(EcoreImportMetadata.NAMESPACE_URI, parsed.namespaceUri.isBlank() ? "urn:emfatic:" + modelId : parsed.namespaceUri);
            annotations.put(EcoreImportMetadata.NAMESPACE_PREFIX, parsed.namespacePrefix.isBlank() ? modelId.replace('-', '_') : parsed.namespacePrefix);

            for(int index = 0; index < parsed.imports.size(); index++) {
                annotations.put("emfatic:import:" + index, parsed.imports.get(index));
            }

            return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, modelId, ImportParsingSupport.pascal(parsed.packageName, "ImportedEmfaticMetamodel"), classes, enums, dataTypes, relations, annotations);
        }

        /** Descripción: Ejecuta la operación «indexClassifiers» correspondiente a la responsabilidad de EmfaticMetamodelImporter. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void indexClassifiers() {
            Map<String, Integer> classNameCounts = countNames(parsed.classes.stream().map(item -> item.name).toList());
            Map<String, Integer> enumNameCounts = countNames(parsed.enums.stream().map(item -> item.name).toList());
            Map<String, Integer> dataTypeNameCounts = countNames(parsed.dataTypes.stream().map(item -> item.name).toList());

            for(ClassSpec item : parsed.classes) {
                String id = classifierId(item.qualifiedName, item.name, classNameCounts.getOrDefault(item.name, 0));
                classIds.put(item.qualifiedName, id);
                if(classNameCounts.getOrDefault(item.name, 0) == 1) uniqueSimpleClassIds.put(item.name, id);
            }

            for(EnumSpec item : parsed.enums) {
                String id = classifierId(item.qualifiedName(), item.name, enumNameCounts.getOrDefault(item.name, 0));
                enumIds.put(item.qualifiedName(), id);
                if(enumNameCounts.getOrDefault(item.name, 0) == 1) uniqueSimpleEnumIds.put(item.name, id);
            }

            for(DataTypeSpec item : parsed.dataTypes) {
                String id = classifierId(item.qualifiedName(), item.name, dataTypeNameCounts.getOrDefault(item.name, 0));
                dataTypeIds.put(item.qualifiedName(), id);
                if(dataTypeNameCounts.getOrDefault(item.name, 0) == 1) uniqueSimpleDataTypeIds.put(item.name, id);
            }
        }

        /** Descripción: Ejecuta la operación «countNames» correspondiente a la responsabilidad de EmfaticMetamodelImporter. Entradas: names: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, Integer> countNames(List<String> names) {
            Map<String, Integer> result = new HashMap<>();
            for(String name : names) result.merge(name, 1, Integer::sum);
            return result;
        }

        /** Descripción: Ejecuta la operación «classifierId» correspondiente a la responsabilidad de EmfaticMetamodelImporter. Entradas: qualifiedName: dato de entrada necesario para la operación; simpleName: dato de entrada necesario para la operación; occurrences: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String classifierId(String qualifiedName, String simpleName, int occurrences) {
            return ImportParsingSupport.safeId(occurrences <= 1 ? simpleName : qualifiedName, "classifier");
        }

        /** Descripción: Crea o prepara el resultado de «buildEnum» dentro de EmfaticMetamodelImporter. Entradas: specification: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaEnum buildEnum(EnumSpec specification) {
            String enumId = enumIds.get(specification.qualifiedName());
            List<MetaEnumLiteral> literals = new ArrayList<>();
            int nextValue = 0;

            for(EnumLiteralSpec literal : specification.literals) {
                int value = literal.value == null ? nextValue : literal.value;
                literals.add(new MetaEnumLiteral(ImportParsingSupport.literalId(enumId, literal.name), literal.name, value, literal.annotations));
                nextValue = value + 1;
            }

            return new MetaEnum(enumId, specification.name, literals, classifierAnnotations(specification.packagePath, specification.annotations));
        }

        /** Descripción: Crea o prepara el resultado de «buildDataType» dentro de EmfaticMetamodelImporter. Entradas: specification: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaDataType buildDataType(DataTypeSpec specification) {
            Map<String, String> annotations = classifierAnnotations(specification.packagePath, specification.annotations);
            annotations.put("emfatic:instanceClassName", specification.instanceClassName);
            annotations.put("emfatic:serializable", String.valueOf(!specification.transientType));
            return new MetaDataType(dataTypeIds.get(specification.qualifiedName()), specification.name, EcoreImportMetadata.dataType(annotations, specification.instanceClassName, !specification.transientType));
        }

        /** Descripción: Crea o prepara el resultado de «buildClass» dentro de EmfaticMetamodelImporter. Entradas: specification: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClass buildClass(ClassSpec specification) {
            String classId = classIds.get(specification.qualifiedName);
            List<MetaAttribute> attributes = new ArrayList<>();
            List<MetaOperation> operations = new ArrayList<>();

            for(AttributeSpec attribute : specification.attributes) {
                MetaTypeReference type = resolveType(attribute.type.name, specification.packagePath);
                Map<String, String> annotations = EcoreImportMetadata.attribute(featureAnnotations(attribute.modifiers, attribute.annotations), !attribute.modifiers.readonly);
                attributes.add(new MetaAttribute(ImportParsingSupport.attributeId(classId, attribute.name), attribute.name, type, attribute.type.multiplicity, attribute.modifiers.ordered, attribute.modifiers.unique, attribute.defaultValue, annotations));
            }

            for(OperationSpec operation : specification.operations) {
                String operationId = "operation:" + classId + ":" + ImportParsingSupport.safeId(operation.name, "operation");
                List<MetaParameter> parameters = new ArrayList<>();

                for(ParameterSpec parameter : operation.parameters) {
                    String parameterId = "parameter:" + operationId + ":" + ImportParsingSupport.safeId(parameter.name, "parameter");
                    parameters.add(new MetaParameter(parameterId, parameter.name, resolveType(parameter.type.name, specification.packagePath), parameter.type.multiplicity, parameter.modifiers.ordered, parameter.modifiers.unique, featureAnnotations(parameter.modifiers, parameter.annotations)));
                }

                MetaTypeReference returnType = operation.returnType.voidType ? null : resolveType(operation.returnType.name, specification.packagePath);
                Multiplicity returnMultiplicity = returnType == null ? null : operation.returnType.multiplicity;
                List<MetaTypeReference> exceptions = operation.exceptionTypes.stream().map(item -> resolveType(item, specification.packagePath)).toList();
                operations.add(new MetaOperation(operationId, operation.name, returnType, returnMultiplicity, operation.modifiers.ordered, operation.modifiers.unique, parameters, exceptions, MetaOperationBehaviorKind.UNSPECIFIED, featureAnnotations(operation.modifiers, operation.annotations)));
            }

            MetaClassKind kind = specification.interfaceType ? MetaClassKind.INTERFACE : specification.abstractType ? MetaClassKind.ABSTRACT_CLASS : MetaClassKind.CLASS;
            Map<String, String> annotations = classifierAnnotations(specification.packagePath, specification.annotations);
            if(!specification.instanceClassName.isBlank()) annotations.put("emfatic:instanceClassName", specification.instanceClassName);
            return new MetaClass(classId, specification.name, kind, attributes, operations, annotations);
        }

        /** Descripción: Crea o prepara el resultado de «buildRelations» dentro de EmfaticMetamodelImporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private List<MetaRelation> buildRelations() {
            List<RelationBuildSpec> pending = new ArrayList<>();

            for(ClassSpec owner : parsed.classes) {
                String sourceId = classIds.get(owner.qualifiedName);

                for(String superType : owner.superTypes) {
                    String targetId = resolveClassId(superType, owner.packagePath, "$.classes[" + owner.name + "].extends");
                    MetaClassKind parentKind = classKind(targetId);
                    MetaRelationKind kind = parentKind == MetaClassKind.INTERFACE && !owner.interfaceType ? MetaRelationKind.IMPLEMENTATION : MetaRelationKind.INHERITANCE;
                    String relationId = (kind == MetaRelationKind.IMPLEMENTATION ? "implementation:" : "supertype:") + sourceId + ":" + targetId;
                    pending.add(new RelationBuildSpec(relationId, "", sourceId, targetId, kind, null, null, "", true, true, Map.of(), owner.qualifiedName, ""));
                }

                for(ReferenceSpec reference : owner.references) {
                    String targetId = resolveClassId(reference.type.name, owner.packagePath, "$.classes[" + owner.name + "].references[" + reference.name + "]");
                    String relationId = ImportParsingSupport.relationId(sourceId, reference.name);
                    Map<String, String> annotations = featureAnnotations(reference.modifiers, reference.annotations);
                    if(!reference.type.oppositeName.isBlank()) annotations.put("emfatic:oppositeName", reference.type.oppositeName);
                    annotations.put("emfatic:resolveProxies", String.valueOf(reference.modifiers.resolve));
                    annotations = EcoreImportMetadata.relation(annotations, !reference.modifiers.readonly, reference.modifiers.resolve);
                    pending.add(new RelationBuildSpec(relationId, reference.name, sourceId, targetId, reference.containment ? MetaRelationKind.CONTAINMENT : MetaRelationKind.REFERENCE, null, reference.type.multiplicity, reference.type.oppositeName, reference.modifiers.ordered, reference.modifiers.unique, annotations, owner.qualifiedName, reference.name));
                }
            }

            Map<String, String> relationIdsByOwnerAndName = new LinkedHashMap<>();
            for(RelationBuildSpec item : pending) {
                if(!item.relationName.isBlank()) relationIdsByOwnerAndName.put(item.ownerQualifiedName + "#" + item.relationName, item.id);
            }

            List<MetaRelation> result = new ArrayList<>();
            for(RelationBuildSpec item : pending) {
                String oppositeId = "";
                if(!item.oppositeName.isBlank()) {
                    String targetQualifiedName = classQualifiedName(item.targetId);
                    oppositeId = relationIdsByOwnerAndName.getOrDefault(targetQualifiedName + "#" + item.oppositeName, "");
                    if(oppositeId.isBlank()) warnings.add(new TransformationWarning("IMPORT_UNRESOLVED_EMFATIC_OPPOSITE", "$.relations[" + item.id + "]", "Opposite reference " + item.oppositeName + " could not be resolved"));
                }
                result.add(new MetaRelation(item.id, item.name, item.sourceId, item.targetId, item.kind, item.sourceMultiplicity, item.targetMultiplicity, oppositeId, item.ordered, item.unique, item.annotations));
            }
            return List.copyOf(result);
        }

        /** Descripción: Ejecuta la operación «classKind» correspondiente a la responsabilidad de EmfaticMetamodelImporter. Entradas: classId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClassKind classKind(String classId) {
            for(ClassSpec item : parsed.classes) {
                if(classIds.get(item.qualifiedName).equals(classId)) return item.interfaceType ? MetaClassKind.INTERFACE : item.abstractType ? MetaClassKind.ABSTRACT_CLASS : MetaClassKind.CLASS;
            }
            return MetaClassKind.INTERFACE;
        }

        /** Descripción: Ejecuta la operación «classQualifiedName» correspondiente a la responsabilidad de EmfaticMetamodelImporter. Entradas: classId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private String classQualifiedName(String classId) {
            for(Map.Entry<String, String> entry : classIds.entrySet()) if(entry.getValue().equals(classId)) return entry.getKey();
            for(MetaClass external : externalClasses.values()) if(external.id().equals(classId)) return external.annotations().getOrDefault("emfatic:qualifiedName", external.name());
            return "";
        }

        /** Descripción: Obtiene la información correspondiente a «resolveClassId» dentro de EmfaticMetamodelImporter. Entradas: typeName: dato de entrada necesario para la operación; packagePath: ruta o ubicación utilizada por la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String resolveClassId(String typeName, String packagePath, String path) {
            String qualified = resolveQualified(typeName, packagePath, classIds, uniqueSimpleClassIds);
            if(!qualified.isBlank()) return classIds.get(qualified);
            String externalName = typeName.isBlank() ? "ExternalType" : typeName;
            String id = ImportParsingSupport.safeId("external-" + externalName, "external-type");
            externalClasses.computeIfAbsent(id, key -> new MetaClass(key, simpleName(externalName), MetaClassKind.INTERFACE, List.of(), List.of(), Map.of("external", "true", "emfatic:qualifiedName", externalName)));
            warnings.add(new TransformationWarning("IMPORT_UNRESOLVED_EMFATIC_CLASS", path, "External EMF class " + externalName + " was preserved as a neutral placeholder interface"));
            return id;
        }

        /** Descripción: Obtiene la información correspondiente a «resolveType» dentro de EmfaticMetamodelImporter. Entradas: typeName: dato de entrada necesario para la operación; packagePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaTypeReference resolveType(String typeName, String packagePath) {
            MetaTypeReference primitive = ImportParsingSupport.typeReference(typeName);
            if(primitive.kind() != MetaTypeReferenceKind.EXTERNAL) return primitive;
            String enumQualified = resolveQualified(typeName, packagePath, enumIds, uniqueSimpleEnumIds);
            if(!enumQualified.isBlank()) return MetaTypeReference.enumType(enumIds.get(enumQualified));
            String dataTypeQualified = resolveQualified(typeName, packagePath, dataTypeIds, uniqueSimpleDataTypeIds);
            if(!dataTypeQualified.isBlank()) return MetaTypeReference.dataType(dataTypeIds.get(dataTypeQualified));
            String classQualified = resolveQualified(typeName, packagePath, classIds, uniqueSimpleClassIds);
            if(!classQualified.isBlank()) return MetaTypeReference.classType(classIds.get(classQualified));
            warnings.add(new TransformationWarning("IMPORT_UNRESOLVED_EMFATIC_TYPE", "$.types[" + typeName + "]", "External EMF type was preserved by name"));
            return MetaTypeReference.external(typeName);
        }

        /** Descripción: Obtiene la información correspondiente a «resolveQualified» dentro de EmfaticMetamodelImporter. Entradas: typeName: dato de entrada necesario para la operación; packagePath: ruta o ubicación utilizada por la operación; qualifiedIds: dato de entrada necesario para la operación; uniqueSimpleIds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String resolveQualified(String typeName, String packagePath, Map<String, String> qualifiedIds, Map<String, String> uniqueSimpleIds) {
            String clean = cleanName(typeName);
            if(qualifiedIds.containsKey(clean)) return clean;
            if(qualifiedIds.containsKey(packagePath + "." + clean)) return packagePath + "." + clean;
            if(qualifiedIds.containsKey(parsed.packageName + "." + clean)) return parsed.packageName + "." + clean;
            String simpleId = uniqueSimpleIds.get(simpleName(clean));
            if(simpleId == null) return "";
            for(Map.Entry<String, String> entry : qualifiedIds.entrySet()) if(entry.getValue().equals(simpleId)) return entry.getKey();
            return "";
        }

        /** Descripción: Ejecuta la operación «classifierAnnotations» correspondiente a la responsabilidad de EmfaticMetamodelImporter. Entradas: packagePath: ruta o ubicación utilizada por la operación; source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, String> classifierAnnotations(String packagePath, Map<String, String> source) {
            Map<String, String> result = new LinkedHashMap<>(source);
            result.put("emfatic:packagePath", packagePath);
            return result;
        }

        /** Descripción: Ejecuta la operación «featureAnnotations» correspondiente a la responsabilidad de EmfaticMetamodelImporter. Entradas: modifiers: dato de entrada necesario para la operación; source: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, String> featureAnnotations(Modifiers modifiers, Map<String, String> source) {
            Map<String, String> result = new LinkedHashMap<>(source);
            result.put("emfatic:changeable", String.valueOf(!modifiers.readonly));
            result.put("emfatic:volatile", String.valueOf(modifiers.volatileFeature));
            result.put("emfatic:transient", String.valueOf(modifiers.transientFeature));
            result.put("emfatic:unsettable", String.valueOf(modifiers.unsettable));
            result.put("emfatic:derived", String.valueOf(modifiers.derived));
            result.put("emfatic:id", String.valueOf(modifiers.id));
            return result;
        }

        /** Descripción: Ejecuta la operación «warnings» correspondiente a la responsabilidad de EmfaticMetamodelImporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private List<TransformationWarning> warnings() {
            return List.copyOf(new LinkedHashSet<>(warnings));
        }
    }

    static final Set<String> MODIFIERS = Set.of("abstract", "readonly", "volatile", "transient", "unsettable", "derived", "unique", "ordered", "resolve", "id");

    static final class Modifiers {
        boolean abstractType;
        boolean readonly;
        boolean volatileFeature;
        boolean transientFeature;
        boolean unsettable;
        boolean derived;
        boolean unique = true;
        boolean ordered = true;
        boolean resolve = true;
        boolean id;

        /** Descripción: Ejecuta la operación «defaults» correspondiente a la responsabilidad de EmfaticMetamodelImporter. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ static Modifiers defaults() { return new Modifiers(); }

        /** Descripción: Actualiza el estado gestionado por EmfaticMetamodelImporter mediante la operación «apply». Entradas: modifier: dato de entrada necesario para la operación; negated: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void apply(String modifier, boolean negated) {
            boolean value = !negated;
            switch(modifier) {
                case "abstract" -> abstractType = value;
                case "readonly" -> readonly = value;
                case "volatile" -> volatileFeature = value;
                case "transient" -> transientFeature = value;
                case "unsettable" -> unsettable = value;
                case "derived" -> derived = value;
                case "unique" -> unique = value;
                case "ordered" -> ordered = value;
                case "resolve" -> resolve = value;
                case "id" -> id = value;
                default -> { }
            }
        }
    }

    enum TokenType { IDENTIFIER, STRING, NUMBER, SYMBOL, EOF }
    record Token(TokenType type, String text, int position) { }
    record ParsedEmfatic(String packageName, String namespaceUri, String namespacePrefix, List<ClassSpec> classes, List<EnumSpec> enums, List<DataTypeSpec> dataTypes, List<String> imports, Map<String, String> annotations, List<TransformationWarning> warnings) { }
    record TypeSpec(String name, Multiplicity multiplicity, String oppositeName, boolean voidType) { static TypeSpec voidSpec() { return new TypeSpec("void", null, "", true); } }
    record AttributeSpec(String name, TypeSpec type, String defaultValue, Modifiers modifiers, Map<String, String> annotations) { }
    record ReferenceSpec(String name, TypeSpec type, boolean containment, Modifiers modifiers, Map<String, String> annotations) { }
    record ParameterSpec(String name, TypeSpec type, Modifiers modifiers, Map<String, String> annotations) { }
    record OperationSpec(String name, TypeSpec returnType, List<ParameterSpec> parameters, List<String> exceptionTypes, Modifiers modifiers, Map<String, String> annotations) { }
    record EnumLiteralSpec(String name, Integer value, Map<String, String> annotations) { }
    record EnumSpec(String packagePath, String name, List<EnumLiteralSpec> literals, Map<String, String> annotations) { private String qualifiedName() { return packagePath + "." + name; } }
    record DataTypeSpec(String packagePath, String name, String instanceClassName, boolean transientType, Map<String, String> annotations) { private String qualifiedName() { return packagePath + "." + name; } }
    record RelationBuildSpec(String id, String name, String sourceId, String targetId, MetaRelationKind kind, Multiplicity sourceMultiplicity, Multiplicity targetMultiplicity, String oppositeName, boolean ordered, boolean unique, Map<String, String> annotations, String ownerQualifiedName, String relationName) { }

    static final class ClassSpec {
        final String packagePath;
        final String name;
        final String qualifiedName;
        final boolean abstractType;
        final boolean interfaceType;
        final String instanceClassName;
        final List<String> superTypes;
        final Map<String, String> annotations;
        final List<AttributeSpec> attributes = new ArrayList<>();
        final List<ReferenceSpec> references = new ArrayList<>();
        final List<OperationSpec> operations = new ArrayList<>();

        /** Descripción: Inicializa una instancia de ClassSpec con sus dependencias y datos de entrada. Entradas: packagePath: ruta o ubicación utilizada por la operación; name: dato de entrada necesario para la operación; abstractType: dato de entrada necesario para la operación; interfaceType: dato de entrada necesario para la operación; instanceClassName: dato de entrada necesario para la operación; superTypes: dato de entrada necesario para la operación; annotations: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ ClassSpec(String packagePath, String name, boolean abstractType, boolean interfaceType, String instanceClassName, List<String> superTypes, Map<String, String> annotations) {
            this.packagePath = packagePath;
            this.name = name;
            this.qualifiedName = packagePath + "." + name;
            this.abstractType = abstractType;
            this.interfaceType = interfaceType;
            this.instanceClassName = instanceClassName == null ? "" : instanceClassName;
            this.superTypes = new ArrayList<>(superTypes);
            this.annotations = new LinkedHashMap<>(annotations);
        }
    }

    /** Descripción: Ejecuta la operación «cleanName» correspondiente a la responsabilidad de EmfaticMetamodelImporter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String cleanName(String value) {
        String result = value == null ? "" : value.trim();
        return result.startsWith("~") ? result.substring(1) : result;
    }

    /** Descripción: Ejecuta la operación «simpleName» correspondiente a la responsabilidad de EmfaticMetamodelImporter. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String simpleName(String value) {
        String clean = cleanName(value);
        int separator = clean.lastIndexOf('.');
        return separator < 0 ? clean : clean.substring(separator + 1);
    }
}
