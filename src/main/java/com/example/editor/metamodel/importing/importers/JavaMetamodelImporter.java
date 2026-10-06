package com.example.editor.metamodel.importing.importers;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

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
import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;
import com.example.editor.metamodel.importing.ImportSourceEntry;
import com.example.editor.metamodel.importing.MetamodelImportFormat;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportOutput;
import com.example.editor.metamodel.importing.MetamodelImportSource;
import com.example.editor.metamodel.importing.MetamodelImporter;
import com.example.editor.metamodel.importing.SafeImportArchiveReader;
import com.example.editor.metamodel.importing.support.ImportParsingSupport;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;

/** Importa declaraciones Java mediante el AST del compilador sin ejecutar código. */
@Component
public class JavaMetamodelImporter implements MetamodelImporter {

    private static final Set<String> SOURCE_EXTENSIONS = Set.of(".java");
    private final SafeImportArchiveReader archiveReader;

    /** Descripción: Inicializa una instancia de JavaMetamodelImporter con sus dependencias y datos de entrada. Entradas: archiveReader: archivo o contenido que debe procesarse. Salida: deja la instancia inicializada y lista para su uso. */ public JavaMetamodelImporter(SafeImportArchiveReader archiveReader) {
        this.archiveReader = archiveReader;
    }

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de JavaFieldSpec. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportFormat format() {
        return MetamodelImportFormat.JAVA;
    }

    @Override
    /** Descripción: Ejecuta la operación «importMetamodel» correspondiente a la responsabilidad de JavaFieldSpec. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportOutput importMetamodel(MetamodelImportSource source, MetamodelImportOptions options) {
        List<ImportSourceEntry> sources = archiveReader.read(source, SOURCE_EXTENSIONS);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if(compiler == null) throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Java import requires a JDK with the compiler API");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<JavaFileObject> files = sources.stream().map(InMemoryJavaSource::new).map(JavaFileObject.class::cast).toList();
        List<String> compilerOptions = List.of("-proc:none", "-Xlint:none", "--release", String.valueOf(Runtime.version().feature()));
        JavacTask task = (JavacTask) compiler.getTask(null, null, diagnostics, compilerOptions, null, files);
        List<CompilationUnitTree> units = new ArrayList<>();
        try {
            task.parse().forEach(units::add);
        } catch(Exception exception) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Could not parse Java sources", exception);
        }
        List<TransformationWarning> warnings = diagnosticWarnings(diagnostics);
        if(diagnostics.getDiagnostics().stream().anyMatch(item -> item.getKind() == Diagnostic.Kind.ERROR)) {
            String message = diagnostics.getDiagnostics().stream().filter(item -> item.getKind() == Diagnostic.Kind.ERROR).limit(8).map(item -> item.getMessage(Locale.ROOT)).reduce((left, right) -> left + "; " + right).orElse("Invalid Java source");
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Java AST parsing failed: " + message);
        }
        ParsedJavaProject project = new AstCollector(sources).collect(units);
        JavaModelBuilder builder = new JavaModelBuilder(source.fileName(), project, warnings);
        return new MetamodelImportOutput(builder.build(), null, builder.warnings());
    }

    /** Descripción: Ejecuta la operación «diagnosticWarnings» correspondiente a la responsabilidad de JavaFieldSpec. Entradas: diagnostics: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<TransformationWarning> diagnosticWarnings(DiagnosticCollector<JavaFileObject> diagnostics) {
        List<TransformationWarning> result = new ArrayList<>();
        for(Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if(diagnostic.getKind() == Diagnostic.Kind.ERROR) continue;
            String path = diagnostic.getSource() == null ? "$" : diagnostic.getSource().getName() + ":" + diagnostic.getLineNumber();
            result.add(new TransformationWarning("IMPORT_JAVA_DIAGNOSTIC", path, diagnostic.getMessage(Locale.ROOT)));
        }
        return result;
    }

    private static final class InMemoryJavaSource extends SimpleJavaFileObject {
        private final String content;
        private final String originalPath;

        /** Descripción: Inicializa una instancia de InMemoryJavaSource con sus dependencias y datos de entrada. Entradas: source: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private InMemoryJavaSource(ImportSourceEntry source) {
            super(URI.create("string:///" + source.path().replace(" ", "%20")), Kind.SOURCE);
            content = new String(source.content(), StandardCharsets.UTF_8);
            originalPath = source.path();
        }

        @Override
        /** Descripción: Obtiene la información correspondiente a «getCharContent» dentro de JavaFieldSpec. Entradas: ignoreEncodingErrors: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }

        @Override
        /** Descripción: Obtiene la información correspondiente a «getName» dentro de JavaFieldSpec. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getName() {
            return originalPath;
        }
    }

    private static final class AstCollector {
        private final Map<String, String> sourceBySimpleName = new HashMap<>();

        /** Descripción: Inicializa una instancia de AstCollector con sus dependencias y datos de entrada. Entradas: sources: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private AstCollector(List<ImportSourceEntry> sources) {
            for(ImportSourceEntry source : sources) {
                String path = source.path();
                String simple = path.substring(path.lastIndexOf('/') + 1);
                sourceBySimpleName.put(simple, path);
            }
        }

        /** Descripción: Ejecuta la operación «collect» correspondiente a la responsabilidad de JavaFieldSpec. Entradas: units: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private ParsedJavaProject collect(List<CompilationUnitTree> units) {
            List<JavaTypeSpec> types = new ArrayList<>();
            Set<String> packages = new LinkedHashSet<>();
            for(CompilationUnitTree unit : units) {
                String packageName = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
                if(!packageName.isBlank()) packages.add(packageName);
                String sourcePath = sourcePath(unit);
                for(Tree declaration : unit.getTypeDecls()) if(declaration instanceof ClassTree classTree) collectType(classTree, packageName, "", sourcePath, types);
            }
            return new ParsedJavaProject(List.copyOf(types), List.copyOf(packages));
        }

        /** Descripción: Ejecuta la operación «collectType» correspondiente a la responsabilidad de JavaFieldSpec. Entradas: tree: dato de entrada necesario para la operación; packageName: dato de entrada necesario para la operación; ownerName: dato de entrada necesario para la operación; sourcePath: ruta o ubicación utilizada por la operación; types: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void collectType(ClassTree tree, String packageName, String ownerName, String sourcePath, List<JavaTypeSpec> types) {
            String simpleName = tree.getSimpleName().toString();
            if(simpleName.isBlank()) return;
            String qualifiedName = packageName.isBlank() ? simpleName : packageName + "." + simpleName;
            if(!ownerName.isBlank()) qualifiedName = ownerName + "." + simpleName;
            JavaTypeKind kind = typeKind(tree.getKind());
            Set<Modifier> modifiers = tree.getModifiers() == null ? Set.of() : Set.copyOf(tree.getModifiers().getFlags());
            List<String> annotations = annotationNames(tree.getModifiers().getAnnotations());
            List<JavaFieldSpec> fields = new ArrayList<>();
            List<JavaMethodSpec> methods = new ArrayList<>();
            List<String> enumLiterals = new ArrayList<>();
            List<JavaTypeSpec> nested = new ArrayList<>();

            for(Tree member : tree.getMembers()) {
                if(member instanceof VariableTree variable) {
                    if(kind == JavaTypeKind.ENUM && variable.getType() == null) enumLiterals.add(variable.getName().toString());
                    else fields.add(field(variable));
                } else if(member instanceof MethodTree method) {
                    if(method.getReturnType() != null) methods.add(method(method));
                } else if(member instanceof ClassTree nestedTree) {
                    collectType(nestedTree, packageName, qualifiedName, sourcePath, nested);
                }
            }

            List<String> superTypes = new ArrayList<>();
            if(tree.getExtendsClause() != null) superTypes.add(tree.getExtendsClause().toString());
            tree.getImplementsClause().forEach(item -> superTypes.add(item.toString()));
            List<String> typeParameters = tree.getTypeParameters().stream().map(TypeParameterTree::getName).map(Object::toString).toList();
            types.add(new JavaTypeSpec(kind, simpleName, qualifiedName, sourcePath, modifiers, annotations, List.copyOf(superTypes), typeParameters, List.copyOf(fields), List.copyOf(methods), List.copyOf(enumLiterals)));
            types.addAll(nested);
        }

        /** Descripción: Ejecuta la operación «field» correspondiente a la responsabilidad de JavaFieldSpec. Entradas: variable: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private JavaFieldSpec field(VariableTree variable) {
            return new JavaFieldSpec(variable.getName().toString(), variable.getType() == null ? "" : variable.getType().toString(),
                    variable.getModifiers() == null ? Set.of() : Set.copyOf(variable.getModifiers().getFlags()),
                    variable.getModifiers() == null ? List.of() : annotationNames(variable.getModifiers().getAnnotations()),
                    variable.getInitializer() == null ? "" : variable.getInitializer().toString());
        }

        /** Descripción: Ejecuta la operación «method» correspondiente a la responsabilidad de JavaFieldSpec. Entradas: method: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private JavaMethodSpec method(MethodTree method) {
            List<JavaParameterSpec> parameters = method.getParameters().stream().map(parameter -> new JavaParameterSpec(parameter.getName().toString(), parameter.getType().toString(),
                    parameter.getModifiers() == null ? List.of() : annotationNames(parameter.getModifiers().getAnnotations()))).toList();
            List<String> exceptions = method.getThrows().stream().map(Object::toString).toList();
            return new JavaMethodSpec(method.getName().toString(), method.getReturnType().toString(), parameters, exceptions,
                    method.getModifiers() == null ? Set.of() : Set.copyOf(method.getModifiers().getFlags()),
                    method.getModifiers() == null ? List.of() : annotationNames(method.getModifiers().getAnnotations()), method.getBody() != null);
        }

        /** Descripción: Ejecuta la operación «annotationNames» correspondiente a la responsabilidad de JavaFieldSpec. Entradas: annotations: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> annotationNames(List<? extends AnnotationTree> annotations) {
            return annotations.stream().map(item -> item.getAnnotationType().toString()).toList();
        }

        /** Descripción: Ejecuta la operación «sourcePath» correspondiente a la responsabilidad de JavaFieldSpec. Entradas: unit: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String sourcePath(CompilationUnitTree unit) {
            if(unit.getSourceFile() == null) return "";
            String name = unit.getSourceFile().getName();
            String simple = name.substring(name.lastIndexOf('/') + 1);
            return sourceBySimpleName.getOrDefault(simple, name);
        }

        /** Descripción: Ejecuta la operación «typeKind» correspondiente a la responsabilidad de JavaFieldSpec. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private JavaTypeKind typeKind(Tree.Kind kind) {
            return switch(kind) {
                case INTERFACE, ANNOTATION_TYPE -> JavaTypeKind.INTERFACE;
                case ENUM -> JavaTypeKind.ENUM;
                case RECORD -> JavaTypeKind.RECORD;
                default -> JavaTypeKind.CLASS;
            };
        }
    }

    private static final class JavaModelBuilder {
        private final String sourceName;
        private final ParsedJavaProject project;
        private final List<TransformationWarning> warnings;
        private final Map<String, JavaTypeSpec> typesByQualified = new LinkedHashMap<>();
        private final Map<String, String> uniqueQualifiedBySimple = new LinkedHashMap<>();
        private final Map<String, String> idsByQualified = new LinkedHashMap<>();
        private final Set<String> structuralInterfaceTargets = new LinkedHashSet<>();

        /** Descripción: Inicializa una instancia de JavaModelBuilder con sus dependencias y datos de entrada. Entradas: sourceName: dato de entrada necesario para la operación; project: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private JavaModelBuilder(String sourceName, ParsedJavaProject project, List<TransformationWarning> warnings) {
            this.sourceName = sourceName;
            this.project = project;
            this.warnings = new ArrayList<>(warnings);
            indexTypes();
            indexStructuralInterfaceTargets();
        }

        /** Descripción: Crea o prepara el resultado de «build» dentro de JavaFieldSpec. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel build() {
            List<MetaClass> classes = new ArrayList<>();
            List<MetaEnum> enums = new ArrayList<>();
            List<MetaRelation> relations = new ArrayList<>();
            for(JavaTypeSpec type : project.types()) {
                if(type.kind() == JavaTypeKind.ENUM) enums.add(buildEnum(type));
                else classes.add(buildClass(type, relations));
            }
            Map<String, String> annotations = new LinkedHashMap<>();
            annotations.put("import:format", "java");
            annotations.put("import:originKind", "IMPORTED");
            annotations.put("import:source", sourceName);
            annotations.put("import:sourceFiles", String.valueOf(project.types().stream().map(JavaTypeSpec::sourcePath).distinct().count()));
            annotations.put("import:packages", String.join(",", project.packages()));
            annotations.put("roundTrip:markerStart", "// <hydra-generated id=\"...\">");
            annotations.put("roundTrip:markerEnd", "// </hydra-generated>");
            annotations.put("roundTrip:preserveManualRegions", "true");
            String base = ImportParsingSupport.fileBaseName(sourceName);
            return new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, ImportParsingSupport.safeId(base, "java-model"), ImportParsingSupport.pascal(base, "JavaModel"), classes, enums, List.of(), relations, annotations);
        }

        /** Descripción: Ejecuta la operación «indexTypes» correspondiente a la responsabilidad de JavaFieldSpec. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void indexTypes() {
            Map<String, Integer> counts = new HashMap<>();
            for(JavaTypeSpec type : project.types()) counts.merge(type.simpleName(), 1, Integer::sum);
            for(JavaTypeSpec type : project.types()) {
                typesByQualified.put(type.qualifiedName(), type);
                idsByQualified.put(type.qualifiedName(), ImportParsingSupport.safeId(type.qualifiedName(), "type"));
                if(counts.get(type.simpleName()) == 1) uniqueQualifiedBySimple.put(type.simpleName(), type.qualifiedName());
            }
        }

        /** Descripción: Ejecuta la operación «indexStructuralInterfaceTargets» correspondiente a la responsabilidad de JavaFieldSpec. Entradas: no requiere parámetros de entrada. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void indexStructuralInterfaceTargets() {
            for(JavaTypeSpec owner : project.types()) {
                for(JavaFieldSpec field : owner.fields()) {
                    if(field.modifiers().contains(Modifier.STATIC)) continue;
                    JavaTypeUse use = parseType(field.type());
                    String target = resolveKnownType(use.baseType(), owner);
                    if(!target.isBlank() && typesByQualified.get(target).kind() == JavaTypeKind.INTERFACE) structuralInterfaceTargets.add(target);
                }
            }
        }

        /** Descripción: Ejecuta la operación «effectiveKind» correspondiente a la responsabilidad de JavaFieldSpec. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClassKind effectiveKind(JavaTypeSpec type) {
            if(type.kind() == JavaTypeKind.INTERFACE) return structuralInterfaceTargets.contains(type.qualifiedName()) ? MetaClassKind.ABSTRACT_CLASS : MetaClassKind.INTERFACE;
            return type.modifiers().contains(Modifier.ABSTRACT) ? MetaClassKind.ABSTRACT_CLASS : MetaClassKind.CLASS;
        }

        /** Descripción: Crea o prepara el resultado de «buildEnum» dentro de JavaFieldSpec. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaEnum buildEnum(JavaTypeSpec type) {
            String id = idsByQualified.get(type.qualifiedName());
            List<MetaEnumLiteral> literals = new ArrayList<>();
            for(int index = 0; index < type.enumLiterals().size(); index++) {
                String literal = type.enumLiterals().get(index);
                literals.add(new MetaEnumLiteral(ImportParsingSupport.literalId(id, literal), literal, index, Map.of("java:qualifiedName", type.qualifiedName() + "." + literal)));
            }
            return new MetaEnum(id, type.simpleName(), literals, annotations(type));
        }

        /** Descripción: Crea o prepara el resultado de «buildClass» dentro de JavaFieldSpec. Entradas: type: dato de entrada necesario para la operación; relations: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClass buildClass(JavaTypeSpec type, List<MetaRelation> relations) {
            String classId = idsByQualified.get(type.qualifiedName());
            List<MetaAttribute> attributes = new ArrayList<>();
            List<MetaOperation> operations = new ArrayList<>();
            for(JavaFieldSpec field : type.fields()) {
                if(field.modifiers().contains(Modifier.STATIC)) continue;
                JavaTypeUse use = parseType(field.type());
                String target = resolveKnownType(use.baseType(), type);
                if(!target.isBlank() && typesByQualified.get(target).kind() != JavaTypeKind.ENUM) {
                    String relationId = ImportParsingSupport.relationId(classId, field.name());
                    Map<String, String> annotations = fieldAnnotations(type, field);
                    annotations.put("java:genericType", field.type());
                    relations.add(new MetaRelation(relationId, field.name(), classId, idsByQualified.get(target), MetaRelationKind.REFERENCE, null, use.multiplicity(), "", !use.collection(), true, annotations));
                } else {
                    MetaTypeReference reference = typeReference(use.baseType(), target);
                    Map<String, String> annotations = fieldAnnotations(type, field);
                    annotations.put("java:genericType", field.type());
                    attributes.add(new MetaAttribute(ImportParsingSupport.attributeId(classId, field.name()), field.name(), reference, use.multiplicity(), !use.collection(), true, field.initializer(), annotations));
                }
            }
            for(JavaMethodSpec method : type.methods()) operations.add(buildOperation(classId, type, method));
            for(String superType : type.superTypes()) {
                JavaTypeUse use = parseType(superType);
                String target = resolveKnownType(use.baseType(), type);
                if(target.isBlank()) {
                    warnings.add(new TransformationWarning("IMPORT_JAVA_EXTERNAL_SUPERTYPE", "$.classes[" + classId + "]", "External supertype was preserved as provenance: " + superType));
                    continue;
                }
                JavaTypeSpec parent = typesByQualified.get(target);
                MetaRelationKind kind = effectiveKind(parent) == MetaClassKind.INTERFACE && effectiveKind(type) != MetaClassKind.INTERFACE ? MetaRelationKind.IMPLEMENTATION : MetaRelationKind.INHERITANCE;
                relations.add(new MetaRelation((kind == MetaRelationKind.IMPLEMENTATION ? "implementation:" : "supertype:") + classId + ":" + idsByQualified.get(target), "", classId, idsByQualified.get(target), kind, null, null, "", true, true, Map.of("java:sourceType", superType)));
            }
            MetaClassKind kind = effectiveKind(type);
            Map<String, String> classAnnotations = annotations(type);
            if(type.kind() == JavaTypeKind.INTERFACE && kind != MetaClassKind.INTERFACE) {
                classAnnotations.put("java:declaredInterface", "true");
                classAnnotations.put("hydra:semanticAdaptation", "STRUCTURAL_INTERFACE_TO_ABSTRACT_CLASS");
                warnings.add(new TransformationWarning("IMPORT_JAVA_STRUCTURAL_INTERFACE_ADAPTED", "$.classes[" + classId + "]", "Java interface was represented as an abstract class because HYDRA structural relations cannot target interfaces"));
            }
            return new MetaClass(classId, type.simpleName(), kind, attributes, operations, classAnnotations);
        }

        /** Descripción: Crea o prepara el resultado de «buildOperation» dentro de JavaFieldSpec. Entradas: classId: identificador que delimita el recurso objetivo; owner: dato de entrada necesario para la operación; method: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaOperation buildOperation(String classId, JavaTypeSpec owner, JavaMethodSpec method) {
            String operationId = "operation:" + classId + ":" + ImportParsingSupport.safeId(method.name(), "operation");
            List<MetaParameter> parameters = new ArrayList<>();
            for(JavaParameterSpec parameter : method.parameters()) {
                JavaTypeUse use = parseType(parameter.type());
                String target = resolveKnownType(use.baseType(), owner);
                parameters.add(new MetaParameter("parameter:" + operationId + ":" + ImportParsingSupport.safeId(parameter.name(), "parameter"), parameter.name(), typeReference(use.baseType(), target), use.multiplicity(), !use.collection(), true, Map.of("java:genericType", parameter.type(), "java:annotations", String.join(",", parameter.annotations()))));
            }
            JavaTypeUse returnUse = parseType(method.returnType());
            boolean voidType = "void".equals(returnUse.baseType());
            String target = voidType ? "" : resolveKnownType(returnUse.baseType(), owner);
            List<MetaTypeReference> exceptions = method.exceptions().stream().map(value -> MetaTypeReference.external(value)).toList();
            Map<String, String> annotations = new LinkedHashMap<>();
            annotations.put("java:modifiers", modifiers(method.modifiers()));
            annotations.put("java:annotations", String.join(",", method.annotations()));
            annotations.put("java:genericReturnType", method.returnType());
            annotations.put("java:bodyImported", "false");
            if(method.hasBody()) warnings.add(new TransformationWarning("IMPORT_JAVA_METHOD_BODY_OMITTED", "$.classes[" + classId + "].operations[" + operationId + "]", "Method bodies are not executed or converted automatically"));
            return new MetaOperation(operationId, method.name(), voidType ? null : typeReference(returnUse.baseType(), target), voidType ? null : returnUse.multiplicity(), !returnUse.collection(), true, parameters, exceptions, MetaOperationBehaviorKind.UNSPECIFIED, annotations);
        }

        /** Descripción: Ejecuta la operación «typeReference» correspondiente a la responsabilidad de JavaFieldSpec. Entradas: baseType: dato de entrada necesario para la operación; knownQualified: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaTypeReference typeReference(String baseType, String knownQualified) {
            if(!knownQualified.isBlank()) {
                JavaTypeSpec known = typesByQualified.get(knownQualified);
                return known.kind() == JavaTypeKind.ENUM ? MetaTypeReference.enumType(idsByQualified.get(knownQualified)) : MetaTypeReference.classType(idsByQualified.get(knownQualified));
            }
            String normalized = baseType.replace("java.lang.", "");
            return switch(normalized) {
                case "String", "CharSequence", "UUID" -> MetaTypeReference.primitive(MetaPrimitiveType.STRING);
                case "boolean", "Boolean" -> MetaTypeReference.primitive(MetaPrimitiveType.BOOLEAN);
                case "byte", "Byte" -> MetaTypeReference.primitive(MetaPrimitiveType.BYTE);
                case "short", "Short" -> MetaTypeReference.primitive(MetaPrimitiveType.SHORT);
                case "int", "Integer" -> MetaTypeReference.primitive(MetaPrimitiveType.INTEGER);
                case "long", "Long", "BigInteger" -> MetaTypeReference.primitive(MetaPrimitiveType.LONG);
                case "float", "Float" -> MetaTypeReference.primitive(MetaPrimitiveType.FLOAT);
                case "double", "Double" -> MetaTypeReference.primitive(MetaPrimitiveType.DOUBLE);
                case "BigDecimal" -> MetaTypeReference.primitive(MetaPrimitiveType.DECIMAL);
                case "char", "Character" -> MetaTypeReference.primitive(MetaPrimitiveType.CHARACTER);
                case "Date", "LocalDate", "LocalDateTime", "Instant", "OffsetDateTime", "ZonedDateTime" -> MetaTypeReference.primitive(MetaPrimitiveType.DATE);
                case "byte[]" -> MetaTypeReference.primitive(MetaPrimitiveType.BINARY);
                case "Object", "?" -> MetaTypeReference.primitive(MetaPrimitiveType.OBJECT);
                default -> MetaTypeReference.external(baseType);
            };
        }

        /** Descripción: Obtiene la información correspondiente a «resolveKnownType» dentro de JavaFieldSpec. Entradas: baseType: dato de entrada necesario para la operación; owner: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String resolveKnownType(String baseType, JavaTypeSpec owner) {
            String clean = baseType.replace("? extends ", "").replace("? super ", "").trim();
            if(typesByQualified.containsKey(clean)) return clean;
            String nested = owner.qualifiedName().contains(".") ? owner.qualifiedName().substring(0, owner.qualifiedName().lastIndexOf('.') + 1) + clean : clean;
            if(typesByQualified.containsKey(nested)) return nested;
            return uniqueQualifiedBySimple.getOrDefault(clean, "");
        }

        /** Descripción: Ejecuta la operación «parseType» correspondiente a la responsabilidad de JavaFieldSpec. Entradas: raw: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private JavaTypeUse parseType(String raw) {
            String value = raw == null ? "Object" : raw.trim();
            if(value.endsWith("...") || value.endsWith("[]") && !"byte[]".equals(value)) return new JavaTypeUse(value.replaceFirst("(?:\\.\\.\\.|\\[\\])$", ""), Multiplicity.many(), true);
            int genericStart = value.indexOf('<');
            if(genericStart > 0 && value.endsWith(">")) {
                String container = value.substring(0, genericStart).replaceAll(".*\\.", "");
                String inner = value.substring(genericStart + 1, value.length() - 1).split(",")[0].trim();
                if(Set.of("List", "Set", "Collection", "Iterable", "Sequence", "Queue", "Deque", "Stream").contains(container)) return new JavaTypeUse(inner, Multiplicity.many(), true);
                if("Optional".equals(container)) return new JavaTypeUse(inner, Multiplicity.optional(), false);
                if("Map".equals(container)) return new JavaTypeUse("Object", Multiplicity.many(), true);
            }
            return new JavaTypeUse(value, Multiplicity.one(), false);
        }

        /** Descripción: Ejecuta la operación «annotations» correspondiente a la responsabilidad de JavaFieldSpec. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, String> annotations(JavaTypeSpec type) {
            Map<String, String> result = new LinkedHashMap<>();
            result.put("java:qualifiedName", type.qualifiedName());
            result.put("java:sourcePath", type.sourcePath());
            result.put("java:kind", type.kind().name());
            result.put("java:modifiers", modifiers(type.modifiers()));
            result.put("java:annotations", String.join(",", type.annotations()));
            result.put("java:typeParameters", String.join(",", type.typeParameters()));
            return result;
        }

        /** Descripción: Ejecuta la operación «fieldAnnotations» correspondiente a la responsabilidad de JavaFieldSpec. Entradas: owner: dato de entrada necesario para la operación; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, String> fieldAnnotations(JavaTypeSpec owner, JavaFieldSpec field) {
            Map<String, String> result = new LinkedHashMap<>();
            result.put("java:qualifiedName", owner.qualifiedName() + "." + field.name());
            result.put("java:modifiers", modifiers(field.modifiers()));
            result.put("java:annotations", String.join(",", field.annotations()));
            return result;
        }

        /** Descripción: Ejecuta la operación «modifiers» correspondiente a la responsabilidad de JavaFieldSpec. Entradas: values: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String modifiers(Set<Modifier> values) {
            return values.stream().map(Modifier::toString).sorted().reduce((left, right) -> left + " " + right).orElse("");
        }

        /** Descripción: Ejecuta la operación «warnings» correspondiente a la responsabilidad de JavaFieldSpec. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private List<TransformationWarning> warnings() {
            return List.copyOf(warnings);
        }
    }

    private enum JavaTypeKind { CLASS, INTERFACE, ENUM, RECORD }
    private record ParsedJavaProject(List<JavaTypeSpec> types, List<String> packages) { }
    private record JavaTypeSpec(JavaTypeKind kind, String simpleName, String qualifiedName, String sourcePath, Set<Modifier> modifiers,
            List<String> annotations, List<String> superTypes, List<String> typeParameters, List<JavaFieldSpec> fields,
            List<JavaMethodSpec> methods, List<String> enumLiterals) { }
    private record JavaFieldSpec(String name, String type, Set<Modifier> modifiers, List<String> annotations, String initializer) { }
    private record JavaMethodSpec(String name, String returnType, List<JavaParameterSpec> parameters, List<String> exceptions,
            Set<Modifier> modifiers, List<String> annotations, boolean hasBody) { }
    private record JavaParameterSpec(String name, String type, List<String> annotations) { }
    private record JavaTypeUse(String baseType, Multiplicity multiplicity, boolean collection) { }
}
