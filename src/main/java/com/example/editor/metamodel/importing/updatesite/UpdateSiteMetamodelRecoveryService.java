package com.example.editor.metamodel.importing.updatesite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.editor.metamodel.core.MetaClassKind;
import com.example.editor.metamodel.core.MetaModel;
import com.example.editor.metamodel.core.Multiplicity;
import com.example.editor.metamodel.importing.MetamodelImportOptions;
import com.example.editor.metamodel.importing.MetamodelImportPreview;
import com.example.editor.metamodel.importing.MetamodelImportService;

/** Descubre y ejecuta los cinco niveles de reconstrucción de metamodelos. */
@Service
public class UpdateSiteMetamodelRecoveryService {
    private final MetamodelImportService metamodelImportService;
    private final UpdateSiteSchemaMetamodelDeriver schemaDeriver;
    private final ClassFileStructureReader classReader;
    private final UpdateSiteSourceMetamodelDeriver sourceDeriver;
    private final UpdateSitePortableSourceMetamodelDeriver portableSourceDeriver;

    @org.springframework.beans.factory.annotation.Autowired
    /** Descripción: Inicializa una instancia de UpdateSiteMetamodelRecoveryService con sus dependencias y datos de entrada. Entradas: metamodelImportService: servicio colaborador utilizado por la operación; schemaDeriver: dato de entrada necesario para la operación; classReader: dato de entrada necesario para la operación; sourceDeriver: dato de entrada necesario para la operación; portableSourceDeriver: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public UpdateSiteMetamodelRecoveryService(MetamodelImportService metamodelImportService,
            UpdateSiteSchemaMetamodelDeriver schemaDeriver, ClassFileStructureReader classReader,
            UpdateSiteSourceMetamodelDeriver sourceDeriver, UpdateSitePortableSourceMetamodelDeriver portableSourceDeriver) {
        this.metamodelImportService = metamodelImportService;
        this.schemaDeriver = schemaDeriver;
        this.classReader = classReader;
        this.sourceDeriver = sourceDeriver;
        this.portableSourceDeriver = portableSourceDeriver;
    }

    /** Constructor de compatibilidad para tests focalizados anteriores a la inferencia desde fuente. */
    /** Descripción: Inicializa una instancia de UpdateSiteMetamodelRecoveryService con sus dependencias y datos de entrada. Entradas: metamodelImportService: servicio colaborador utilizado por la operación; schemaDeriver: dato de entrada necesario para la operación; classReader: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ UpdateSiteMetamodelRecoveryService(MetamodelImportService metamodelImportService,
            UpdateSiteSchemaMetamodelDeriver schemaDeriver, ClassFileStructureReader classReader) {
        this(metamodelImportService, schemaDeriver, classReader, new UpdateSiteSourceMetamodelDeriver(), new UpdateSitePortableSourceMetamodelDeriver());
    }

    /** Descripción: Ejecuta la operación «discover» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: repository: dato de entrada necesario para la operación; summary: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ List<UpdateSiteRecoveryCandidate> discover(UpdateSiteRepositoryContent repository,
            UpdateSiteRepositorySummary summary) {
        List<UpdateSiteArtifactDescriptor> artifacts = new ArrayList<>();
        summary.bundles().forEach(bundle -> artifacts.addAll(bundle.artifacts()));
        artifacts.addAll(summary.unownedArtifacts());
        Map<String, UpdateSiteArtifactDescriptor> unique = new LinkedHashMap<>();
        artifacts.forEach(item -> unique.putIfAbsent(item.path(), item));
        List<UpdateSiteRecoveryCandidate> candidates = new ArrayList<>();

        for(UpdateSiteArtifactDescriptor artifact : unique.values()) {
            CandidatePolicy policy = directPolicy(artifact.kind());
            String candidateFormat = artifact.kind().name();
            if(artifact.kind() == UpdateSiteArtifactKind.JSON) {
                byte[] json = repository.find(artifact.path()).orElse(new byte[0]);
                if(looksLikeNeutralMetamodel(json)) {
                    policy = new CandidatePolicy(UpdateSiteConfidence.EXACT, 92,
                            "Neutral JSON metamodel candidate found", List.of());
                } else {
                    candidateFormat = looksLikeJsonSchema(json) ? UpdateSiteArtifactKind.JSON_SCHEMA.name() : "JSON_DATA";
                    policy = new CandidatePolicy(UpdateSiteConfidence.DERIVED, looksLikeJsonSchema(json) ? 83 : 74,
                            looksLikeJsonSchema(json)
                                    ? "JSON Schema found and transformed into neutral classes and properties"
                                    : "JSON data structure can be derived into neutral classes and properties",
                            List.of("JSON-derived multiplicities and references require review"));
                }
            }
            if(policy != null) {
                candidates.add(candidate(artifact, candidateFormat, 1, UpdateSiteRecoveryMethod.DIRECT_METAMODEL,
                        policy.confidence(), policy.score(), true, policy.rationale(), policy.warnings()));
            }
            if(artifact.kind() == UpdateSiteArtifactKind.GENMODEL) {
                candidates.add(candidate(artifact, 2, UpdateSiteRecoveryMethod.GENMODEL,
                        UpdateSiteConfidence.RECOVERED, 72, true,
                        "GenModel can recover package, class, feature and generation metadata when Ecore is absent",
                        List.of("GenModel may reference an Ecore resource that is not present in the update site")));
            }
            if((artifact.kind() == UpdateSiteArtifactKind.PLUGIN_XML || artifact.kind() == UpdateSiteArtifactKind.FRAGMENT_XML)
                    && generatedPackageRegistration(repository, artifact.path())) {
                candidates.add(candidate(artifact, 3, UpdateSiteRecoveryMethod.PLUGIN_REGISTRY,
                        UpdateSiteConfidence.RECOVERED, 60, true,
                        artifact.kind() == UpdateSiteArtifactKind.FRAGMENT_XML
                                ? "fragment.xml registers an org.eclipse.emf.ecore.generated_package extension"
                                : "plugin.xml registers an org.eclipse.emf.ecore.generated_package extension",
                        List.of("The registry exposes package identity but may not contain all structural features")));
            }
            if((artifact.kind() == UpdateSiteArtifactKind.MANIFEST || artifact.kind() == UpdateSiteArtifactKind.BUILD_PROPERTIES)
                    && exposesGeneratedModelMetadata(repository, artifact.path())) {
                candidates.add(candidate(artifact, 3, UpdateSiteRecoveryMethod.PLUGIN_REGISTRY,
                        UpdateSiteConfidence.RECOVERED, 48, true,
                        "Plug-in metadata exposes generated packages or model resources",
                        List.of("Only package identity and generated-resource hints can be recovered from this descriptor")));
            }
            if(artifact.kind() == UpdateSiteArtifactKind.JAVA_CLASS && isEmfPackageClass(artifact.path())) {
                candidates.add(candidate(artifact, 4, UpdateSiteRecoveryMethod.EMF_BYTECODE,
                        UpdateSiteConfidence.RECOVERED, artifact.path().toLowerCase(Locale.ROOT).endsWith("package.class") ? 56 : 52,
                        true, "Generated EMF Package bytecode exposes EClass, EAttribute, EReference and EOperation accessors",
                        List.of("Documentation and user EAnnotations may not be present in bytecode")));
            }
        }

        Map<String, UpdateSiteArtifactDescriptor> sourceInferenceByBundle = new LinkedHashMap<>();
        unique.values().stream().filter(item -> item.kind() == UpdateSiteArtifactKind.JAVA_SOURCE || item.kind() == UpdateSiteArtifactKind.KOTLIN_SOURCE
                        || item.kind() == UpdateSiteArtifactKind.TYPESCRIPT_SOURCE || item.kind() == UpdateSiteArtifactKind.JAVASCRIPT_SOURCE || item.kind() == UpdateSiteArtifactKind.GO_SOURCE)
                .forEach(item -> sourceInferenceByBundle.putIfAbsent(item.bundleId().isBlank() ? "repository-source" : item.bundleId(), item));
        for(UpdateSiteArtifactDescriptor artifact : sourceInferenceByBundle.values()) {
            candidates.add(candidate(artifact, 5, UpdateSiteRecoveryMethod.SOURCE_INFERENCE,
                    UpdateSiteConfidence.INFERRED, 38, true,
                    "Java/Kotlin/TypeScript/Go declarations and structural features can be inferred without executing repository code",
                    List.of("Source inference is fallback evidence; annotations, generated semantics and runtime behavior require review")));
        }

        Map<String, UpdateSiteArtifactDescriptor> inferenceByBundle = new LinkedHashMap<>();
        unique.values().stream().filter(item -> item.kind() == UpdateSiteArtifactKind.JAVA_CLASS)
                .filter(item -> !isInfrastructureClass(item.path())).forEach(item -> inferenceByBundle.putIfAbsent(item.bundleId(), item));
        for(UpdateSiteArtifactDescriptor artifact : inferenceByBundle.values()) {
            candidates.add(candidate(artifact, 6, UpdateSiteRecoveryMethod.CODE_INFERENCE,
                    UpdateSiteConfidence.INFERRED, 30, true,
                    "Class fields, getters, setters, collections, inheritance and generic descriptors can be inferred",
                    List.of("The result is inferred and must not be presented as the original metamodel")));
        }

        Map<String, UpdateSiteRecoveryCandidate> deduplicated = new LinkedHashMap<>();
        candidates.stream().sorted(Comparator.comparingInt(UpdateSiteRecoveryCandidate::score).reversed()
                .thenComparing(UpdateSiteRecoveryCandidate::sourcePath))
                .forEach(item -> deduplicated.putIfAbsent(item.method() + ":" + item.sourcePath(), item));
        return List.copyOf(deduplicated.values());
    }

    /** Descripción: Ejecuta la operación «recover» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: candidate: dato de entrada necesario para la operación; repository: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ MetaModel recover(UpdateSiteRecoveryCandidate candidate, UpdateSiteRepositoryContent repository, String targetId) {
        byte[] content = repository.find(candidate.sourcePath()).orElseThrow(() ->
                new IllegalArgumentException("Recovery source was not found: " + candidate.sourcePath()));
        return switch(candidate.method()) {
            case DIRECT_METAMODEL -> recoverDirect(candidate, content, targetId);
            case GENMODEL -> schemaDeriver.derive(UpdateSiteArtifactKind.GENMODEL, content, targetId, candidate.sourcePath());
            case PLUGIN_REGISTRY -> schemaDeriver.derive(UpdateSiteArtifactKinds.fromPath(candidate.sourcePath()), content,
                    targetId, candidate.sourcePath());
            case EMF_BYTECODE -> recoverEmfBytecode(candidate, repository, targetId);
            case SOURCE_INFERENCE -> recoverSourceInference(candidate, repository, targetId);
            case CODE_INFERENCE -> inferFromCode(candidate, repository, targetId);
        };
    }

    /** Descripción: Ejecuta la operación «recoverDirect» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: candidate: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse; targetId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel recoverDirect(UpdateSiteRecoveryCandidate candidate, byte[] content, String targetId) {
        UpdateSiteArtifactKind kind = UpdateSiteArtifactKinds.fromPath(candidate.sourcePath());
        if(kind == UpdateSiteArtifactKind.XCORE) return portableSourceDeriver.derive(kind, content, targetId, candidate.sourcePath());
        if(candidate.format().equals(UpdateSiteArtifactKind.JSON_SCHEMA.name()) || candidate.format().equals("JSON_DATA")) {
            return schemaDeriver.derive(UpdateSiteArtifactKind.JSON_SCHEMA, content, targetId, candidate.sourcePath());
        }
        String format = importFormat(kind);
        if(format != null) {
            MetamodelImportPreview preview = metamodelImportService.preview(fileName(candidate.sourcePath()), mediaType(kind), content,
                    new MetamodelImportOptions(format, targetId, false, true, true, false));
            return preview.metamodel();
        }
        return schemaDeriver.derive(kind, content, targetId, candidate.sourcePath());
    }

    /** Descripción: Ejecuta la operación «recoverEmfBytecode» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: candidate: dato de entrada necesario para la operación; repository: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel recoverEmfBytecode(UpdateSiteRecoveryCandidate candidate, UpdateSiteRepositoryContent repository,
            String targetId) {
        RecoveredMetamodelBuilder builder = new RecoveredMetamodelBuilder();
        String bundleId = candidate.sourceBundleId();
        List<Map.Entry<String, byte[]>> classes = repository.files().entrySet().stream()
                .filter(entry -> UpdateSiteArtifactKinds.fromPath(entry.getKey()) == UpdateSiteArtifactKind.JAVA_CLASS)
                .filter(entry -> bundleId.isBlank() || sameBundle(entry.getKey(), candidate.sourcePath()))
                .filter(entry -> isEmfPackageClass(entry.getKey())).sorted(Map.Entry.comparingByKey()).toList();
        for(Map.Entry<String, byte[]> entry : classes) {
            ClassFileStructure structure;
            try { structure = classReader.read(entry.getValue()); } catch(IllegalArgumentException exception) { continue; }
            for(ClassFileStructure.ClassMember method : structure.methods()) {
                String returnType = methodReturnType(method.descriptor());
                if(returnType.endsWith("org.eclipse.emf.ecore.EClass") && method.name().startsWith("get")) {
                    builder.addClass(method.name().substring(3), MetaClassKind.CLASS);
                } else if(method.name().startsWith("get") && method.name().contains("_")
                        && (returnType.endsWith("EAttribute") || returnType.endsWith("EReference") || returnType.endsWith("EOperation"))) {
                    String feature = method.name().substring(3);
                    int separator = feature.indexOf('_');
                    String owner = feature.substring(0, separator);
                    String member = feature.substring(separator + 1).replaceAll("__.*$", "");
                    if(returnType.endsWith("EReference")) builder.addRelation(owner, member, "RecoveredTarget", Multiplicity.optional(), false);
                    else if(returnType.endsWith("EOperation")) builder.addOperation(owner, member, "void", List.of());
                    else builder.addAttribute(owner, member, "Object", Multiplicity.optional());
                }
            }
        }
        return builder.build(targetId, fileStem(candidate.sourcePath()), Map.of("hydra.recovery.bytecode", "EMF_PACKAGE"));
    }

    /** Descripción: Ejecuta la operación «inferFromCode» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: candidate: dato de entrada necesario para la operación; repository: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel inferFromCode(UpdateSiteRecoveryCandidate candidate, UpdateSiteRepositoryContent repository,
            String targetId) {
        RecoveredMetamodelBuilder builder = new RecoveredMetamodelBuilder();
        List<Map.Entry<String, byte[]>> classes = repository.files().entrySet().stream()
                .filter(entry -> UpdateSiteArtifactKinds.fromPath(entry.getKey()) == UpdateSiteArtifactKind.JAVA_CLASS)
                .filter(entry -> candidate.sourceBundleId().isBlank() || sameBundle(entry.getKey(), candidate.sourcePath()))
                .filter(entry -> !isInfrastructureClass(entry.getKey())).sorted(Map.Entry.comparingByKey()).toList();
        Set<String> knownClasses = new LinkedHashSet<>();
        Map<String, ClassFileStructure> structures = new LinkedHashMap<>();
        for(Map.Entry<String, byte[]> entry : classes) {
            try {
                ClassFileStructure structure = classReader.read(entry.getValue());
                String simple = simpleName(structure.className());
                if(simple.isBlank() || simple.contains("$") || simple.equals("package-info") || simple.equals("module-info")) continue;
                knownClasses.add(simple); structures.put(simple, structure);
                MetaClassKind kind = (structure.accessFlags() & 0x0200) != 0 ? MetaClassKind.INTERFACE
                        : (structure.accessFlags() & 0x0400) != 0 ? MetaClassKind.ABSTRACT_CLASS : MetaClassKind.CLASS;
                builder.addClass(simple, kind).mergeKind(kind);
            } catch(IllegalArgumentException exception) {
                // A malformed class is isolated; other classes can still be inferred.
            }
        }
        for(Map.Entry<String, ClassFileStructure> entry : structures.entrySet()) {
            String className = entry.getKey();
            ClassFileStructure structure = entry.getValue();
            String superName = simpleName(structure.superClassName());
            if(knownClasses.contains(superName) && !superName.equals(className)) builder.addInheritance(className, superName);
            for(ClassFileStructure.ClassMember field : structure.fields()) {
                if((field.accessFlags() & 0x0008) != 0 || field.name().startsWith("$")) continue;
                TypeDescriptor type = fieldType(field.descriptor());
                if(knownClasses.contains(simpleName(type.name()))) builder.addRelation(className, field.name(), simpleName(type.name()), type.many() ? Multiplicity.many() : Multiplicity.optional(), false);
                else builder.addAttribute(className, field.name(), type.name(), type.many() ? Multiplicity.many() : Multiplicity.optional());
            }
            for(ClassFileStructure.ClassMember method : structure.methods()) {
                if(method.name().startsWith("get") && method.name().length() > 3 && methodParameters(method.descriptor()).isEmpty()) {
                    TypeDescriptor type = methodType(method.descriptor());
                    String property = decapitalize(method.name().substring(3));
                    if(knownClasses.contains(simpleName(type.name()))) builder.addRelation(className, property, simpleName(type.name()), type.many() ? Multiplicity.many() : Multiplicity.optional(), false);
                    else builder.addAttribute(className, property, type.name(), type.many() ? Multiplicity.many() : Multiplicity.optional());
                } else if(!method.name().startsWith("<") && (method.accessFlags() & 0x0001) != 0) {
                    List<RecoveredMetamodelBuilder.ParameterSpec> parameters = new ArrayList<>();
                    int index = 0;
                    for(TypeDescriptor parameter : methodParameters(method.descriptor())) parameters.add(new RecoveredMetamodelBuilder.ParameterSpec("parameter" + index++, parameter.name(), parameter.many()));
                    builder.addOperation(className, method.name(), methodReturnType(method.descriptor()), parameters);
                }
            }
        }
        return builder.build(targetId, fileStem(candidate.sourcePath()), Map.of("hydra.recovery.inference", "JVM_CLASS_STRUCTURE"));
    }

    /** Descripción: Ejecuta la operación «recoverSourceInference» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: candidate: dato de entrada necesario para la operación; repository: dato de entrada necesario para la operación; targetId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel recoverSourceInference(UpdateSiteRecoveryCandidate candidate, UpdateSiteRepositoryContent repository, String targetId) {
        UpdateSiteArtifactKind kind = UpdateSiteArtifactKinds.fromPath(candidate.sourcePath());
        if(kind == UpdateSiteArtifactKind.TYPESCRIPT_SOURCE || kind == UpdateSiteArtifactKind.JAVASCRIPT_SOURCE || kind == UpdateSiteArtifactKind.GO_SOURCE) {
            byte[] content = repository.find(candidate.sourcePath()).orElseThrow(() -> new IllegalArgumentException("Recovery source was not found: " + candidate.sourcePath()));
            return portableSourceDeriver.derive(kind, content, targetId, candidate.sourcePath());
        }
        return sourceDeriver.derive(repository, targetId, candidate.sourcePath());
    }

    /** Descripción: Ejecuta la operación «directPolicy» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private CandidatePolicy directPolicy(UpdateSiteArtifactKind kind) {
        return switch(kind) {
            case ECORE -> new CandidatePolicy(UpdateSiteConfidence.EXACT, 100,
                    "Original Ecore metamodel found; EPackage structures can be converted directly", List.of());
            case XCORE -> new CandidatePolicy(UpdateSiteConfidence.RECOVERED, 91,
                    "Xcore textual metamodel found and statically reconstructed without executing generator code", List.of("Advanced Xcore expressions and annotations require review"));
            case EMFATIC -> new CandidatePolicy(UpdateSiteConfidence.RECOVERED, 92,
                    "EMF/Emfatic textual metamodel found and converted by the canonical importer", List.of());
            case XMI -> new CandidatePolicy(UpdateSiteConfidence.EXACT, 94,
                    "Original EPackage XMI candidate found", List.of("The root is validated as EPackage during preview"));
            case JSON -> new CandidatePolicy(UpdateSiteConfidence.DERIVED, 74,
                    "JSON structure can be transformed into a neutral metamodel", List.of("JSON semantics require review"));
            case UML -> new CandidatePolicy(UpdateSiteConfidence.DERIVED, 88,
                    "UML metamodel source found and transformed into the neutral core", List.of("UML profiles and advanced association semantics may require review"));
            case XSD -> new CandidatePolicy(UpdateSiteConfidence.DERIVED, 84,
                    "XML Schema found and transformed into neutral classes and features", List.of());
            case JSON_SCHEMA -> new CandidatePolicy(UpdateSiteConfidence.DERIVED, 83,
                    "JSON Schema found and transformed into neutral classes and properties", List.of());
            case GRAPHQL -> new CandidatePolicy(UpdateSiteConfidence.DERIVED, 82,
                    "GraphQL schema found and transformed into neutral classes, enums and relations", List.of());
            case PROTO -> new CandidatePolicy(UpdateSiteConfidence.DERIVED, 81,
                    "Protocol Buffers schema found and transformed into neutral messages and fields", List.of());
            case XTEXT_GRAMMAR -> new CandidatePolicy(UpdateSiteConfidence.DERIVED, 80,
                    "Xtext grammar found and converted through the grammar importer", List.of());
            case LANGIUM_GRAMMAR -> new CandidatePolicy(UpdateSiteConfidence.DERIVED, 79,
                    "Langium grammar found and converted through the grammar importer", List.of());
            case ANTLR_GRAMMAR -> new CandidatePolicy(UpdateSiteConfidence.DERIVED, 77,
                    "ANTLR grammar found and converted through the grammar importer", List.of("Grammar semantics are structural and require review"));
            case PYTHON_SOURCE -> new CandidatePolicy(UpdateSiteConfidence.INFERRED, 58,
                    "Python source model found and converted by the non-executing source importer", List.of("Runtime behavior is never executed during recovery"));
            case BLOCKLY_JSON, BLOCKLY_JAVASCRIPT -> new CandidatePolicy(UpdateSiteConfidence.DERIVED, 66,
                    "Blockly block definitions found and converted into neutral structural concepts", List.of("Block behavior code is not executed"));
            case PLANTUML -> new CandidatePolicy(UpdateSiteConfidence.DERIVED, 64,
                    "PlantUML class diagram found and converted into a neutral structural model", List.of("Diagram-only semantics may be incomplete"));
            default -> null;
        };
    }

    /** Descripción: Evalúa la condición «candidate» sin modificar el estado externo. Entradas: artifact: dato de entrada necesario para la operación; level: dato de entrada necesario para la operación; method: dato de entrada necesario para la operación; confidence: dato de entrada necesario para la operación; score: dato de entrada necesario para la operación; importable: dato de entrada necesario para la operación; rationale: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private UpdateSiteRecoveryCandidate candidate(UpdateSiteArtifactDescriptor artifact, int level,
            UpdateSiteRecoveryMethod method, UpdateSiteConfidence confidence, int score, boolean importable,
            String rationale, List<String> warnings) {
        return candidate(artifact, artifact.kind().name(), level, method, confidence, score, importable, rationale, warnings);
    }

    /** Descripción: Evalúa la condición «candidate» sin modificar el estado externo. Entradas: artifact: dato de entrada necesario para la operación; format: dato de entrada necesario para la operación; level: dato de entrada necesario para la operación; method: dato de entrada necesario para la operación; confidence: dato de entrada necesario para la operación; score: dato de entrada necesario para la operación; importable: dato de entrada necesario para la operación; rationale: dato de entrada necesario para la operación; warnings: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private UpdateSiteRecoveryCandidate candidate(UpdateSiteArtifactDescriptor artifact, String format, int level,
            UpdateSiteRecoveryMethod method, UpdateSiteConfidence confidence, int score, boolean importable,
            String rationale, List<String> warnings) {
        String id = "candidate-" + UpdateSiteHashing.sha256((method + ":" + artifact.path() + ":" + format)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0, 16);
        return new UpdateSiteRecoveryCandidate(id, level, method, confidence, artifact.path(), artifact.bundleId(),
                format, score, importable, rationale, warnings);
    }

    /** Descripción: Crea o prepara el resultado de «generatedPackageRegistration» dentro de UpdateSiteMetamodelRecoveryService. Entradas: repository: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean generatedPackageRegistration(UpdateSiteRepositoryContent repository, String path) {
        return repository.find(path).map(content -> new String(content, java.nio.charset.StandardCharsets.UTF_8)
                .contains("org.eclipse.emf.ecore.generated_package")).orElse(false);
    }

    /** Descripción: Ejecuta la operación «exposesGeneratedModelMetadata» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: repository: dato de entrada necesario para la operación; path: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean exposesGeneratedModelMetadata(UpdateSiteRepositoryContent repository, String path) {
        return repository.find(path).map(content -> {
            String text = new String(content, java.nio.charset.StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            return text.contains("export-package") || text.contains("ecore") || text.contains("genmodel")
                    || text.contains("source..") || text.contains("bin.includes");
        }).orElse(false);
    }

    /** Descripción: Ejecuta la operación «looksLikeNeutralMetamodel» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean looksLikeNeutralMetamodel(byte[] content) {
        String text = new String(content, java.nio.charset.StandardCharsets.UTF_8);
        return text.contains("\"schemaVersion\"") && (text.contains("\"classes\"") || text.contains("\"relations\""));
    }

    /** Descripción: Ejecuta la operación «looksLikeJsonSchema» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean looksLikeJsonSchema(byte[] content) {
        String text = new String(content, java.nio.charset.StandardCharsets.UTF_8);
        return text.contains("\"$schema\"") || text.contains("\"$defs\"")
                || text.contains("\"definitions\"") || text.contains("\"properties\"");
    }

    /** Descripción: Evalúa la condición «isEmfPackageClass» sin modificar el estado externo. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isEmfPackageClass(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith("package.class") || lower.endsWith("packageimpl.class");
    }

    /** Descripción: Evalúa la condición «isInfrastructureClass» sin modificar el estado externo. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isInfrastructureClass(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith("package.class") || lower.endsWith("packageimpl.class")
                || lower.endsWith("factory.class") || lower.endsWith("factoryimpl.class")
                || lower.endsWith("switch.class") || lower.endsWith("adapterfactory.class")
                || lower.endsWith("plugin.class") || lower.contains("/internal/");
    }

    /** Descripción: Ejecuta la operación «sameBundle» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: path: ruta o ubicación utilizada por la operación; referencePath: ruta o ubicación utilizada por la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean sameBundle(String path, String referencePath) {
        return archiveRoot(path).equals(archiveRoot(referencePath));
    }

    /** Descripción: Ejecuta la operación «archiveRoot» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String archiveRoot(String path) {
        int marker = path.indexOf("!/");
        return marker >= 0 ? path.substring(0, marker) : path;
    }

    /** Descripción: Ejecuta la operación «importFormat» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String importFormat(UpdateSiteArtifactKind kind) {
        return switch(kind) {
            case ECORE -> "ecore";
            case EMFATIC -> "emf";
            case XMI -> "xmi";
            case JSON -> "json";
            case XTEXT_GRAMMAR -> "xtext";
            case LANGIUM_GRAMMAR -> "langium";
            case ANTLR_GRAMMAR -> "antlr";
            case PYTHON_SOURCE -> "python";
            case BLOCKLY_JSON -> "blockly-json";
            case BLOCKLY_JAVASCRIPT -> "blockly-javascript";
            case PLANTUML -> "plantuml";
            default -> null;
        };
    }

    /** Descripción: Ejecuta la operación «mediaType» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String mediaType(UpdateSiteArtifactKind kind) {
        return kind == UpdateSiteArtifactKind.JSON || kind == UpdateSiteArtifactKind.BLOCKLY_JSON ? "application/json"
                : kind == UpdateSiteArtifactKind.XTEXT_GRAMMAR || kind == UpdateSiteArtifactKind.LANGIUM_GRAMMAR
                || kind == UpdateSiteArtifactKind.ANTLR_GRAMMAR || kind == UpdateSiteArtifactKind.EMFATIC
                || kind == UpdateSiteArtifactKind.PYTHON_SOURCE || kind == UpdateSiteArtifactKind.BLOCKLY_JAVASCRIPT
                || kind == UpdateSiteArtifactKind.PLANTUML ? "text/plain" : "application/xml";
    }

    /** Descripción: Ejecuta la operación «fileName» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String fileName(String path) {
        String result = path.contains("!/") ? path.substring(path.lastIndexOf("!/") + 2) : path;
        return result.contains("/") ? result.substring(result.lastIndexOf('/') + 1) : result;
    }

    /** Descripción: Ejecuta la operación «fileStem» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String fileStem(String path) {
        String name = fileName(path);
        int dot = name.indexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Descripción: Ejecuta la operación «simpleName» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: className: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String simpleName(String className) {
        if(className == null || className.isBlank()) return "";
        int separator = Math.max(className.lastIndexOf('.'), className.lastIndexOf('/'));
        return separator >= 0 ? className.substring(separator + 1) : className;
    }

    /** Descripción: Ejecuta la operación «decapitalize» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String decapitalize(String value) {
        return value.isBlank() ? value : Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    /** Descripción: Ejecuta la operación «fieldType» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: descriptor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TypeDescriptor fieldType(String descriptor) {
        return parseType(descriptor, new int[]{0});
    }

    /** Descripción: Ejecuta la operación «methodType» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: descriptor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TypeDescriptor methodType(String descriptor) {
        int marker = descriptor.indexOf(')');
        return marker < 0 ? new TypeDescriptor("Object", false) : parseType(descriptor, new int[]{marker + 1});
    }

    /** Descripción: Ejecuta la operación «methodReturnType» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: descriptor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String methodReturnType(String descriptor) {
        return methodType(descriptor).name();
    }

    /** Descripción: Ejecuta la operación «methodParameters» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: descriptor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<TypeDescriptor> methodParameters(String descriptor) {
        List<TypeDescriptor> result = new ArrayList<>();
        int[] index = {descriptor.indexOf('(') + 1};
        int end = descriptor.indexOf(')');
        while(index[0] > 0 && index[0] < end) result.add(parseType(descriptor, index));
        return result;
    }

    /** Descripción: Ejecuta la operación «parseType» correspondiente a la responsabilidad de UpdateSiteMetamodelRecoveryService. Entradas: descriptor: dato de entrada necesario para la operación; index: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TypeDescriptor parseType(String descriptor, int[] index) {
        boolean many = false;
        while(index[0] < descriptor.length() && descriptor.charAt(index[0]) == '[') { many = true; index[0]++; }
        if(index[0] >= descriptor.length()) return new TypeDescriptor("Object", many);
        char token = descriptor.charAt(index[0]++);
        return switch(token) {
            case 'V' -> new TypeDescriptor("void", many);
            case 'Z' -> new TypeDescriptor("Boolean", many);
            case 'B' -> new TypeDescriptor("Byte", many);
            case 'C' -> new TypeDescriptor("Character", many);
            case 'S' -> new TypeDescriptor("Short", many);
            case 'I' -> new TypeDescriptor("Integer", many);
            case 'J' -> new TypeDescriptor("Long", many);
            case 'F' -> new TypeDescriptor("Float", many);
            case 'D' -> new TypeDescriptor("Double", many);
            case 'L' -> {
                int end = descriptor.indexOf(';', index[0]);
                if(end < 0) { index[0] = descriptor.length(); yield new TypeDescriptor("Object", many); }
                String name = descriptor.substring(index[0], end).replace('/', '.');
                index[0] = end + 1;
                boolean collection = name.equals("java.util.List") || name.equals("java.util.Set") || name.equals("java.util.Collection");
                yield new TypeDescriptor(name, many || collection);
            }
            default -> new TypeDescriptor("Object", many);
        };
    }

    private record CandidatePolicy(UpdateSiteConfidence confidence, int score, String rationale, List<String> warnings) { }
    private record TypeDescriptor(String name, boolean many) { }
}
