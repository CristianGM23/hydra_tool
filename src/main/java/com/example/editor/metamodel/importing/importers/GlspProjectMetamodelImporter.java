package com.example.editor.metamodel.importing.importers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Importa la estructura semántica recuperable de un proyecto Eclipse GLSP. */
@Component
public class GlspProjectMetamodelImporter implements MetamodelImporter {

    private static final Set<String> PROJECT_EXTENSIONS = Set.of(".ecore", ".java", ".py", ".ts", ".tsx", ".json", ".xml", ".yaml", ".yml", ".properties", ".gradle", ".kts");
    private final SafeImportArchiveReader archiveReader;
    private final EcoreMetamodelImporter ecoreImporter;
    private final JavaMetamodelImporter javaImporter;
    private final PythonMetamodelImporter pythonImporter;
    private final ObjectMapper objectMapper;

    /** Descripción: Inicializa una instancia de GlspProjectMetamodelImporter con sus dependencias y datos de entrada. Entradas: archiveReader: archivo o contenido que debe procesarse; ecoreImporter: dato de entrada necesario para la operación; javaImporter: dato de entrada necesario para la operación; pythonImporter: dato de entrada necesario para la operación; objectMapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public GlspProjectMetamodelImporter(SafeImportArchiveReader archiveReader, EcoreMetamodelImporter ecoreImporter,
            JavaMetamodelImporter javaImporter, PythonMetamodelImporter pythonImporter, ObjectMapper objectMapper) {
        this.archiveReader = archiveReader;
        this.ecoreImporter = ecoreImporter;
        this.javaImporter = javaImporter;
        this.pythonImporter = pythonImporter;
        this.objectMapper = objectMapper;
    }

    @Override
    /** Descripción: Ejecuta la operación «format» correspondiente a la responsabilidad de TypeScriptType. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportFormat format() {
        return MetamodelImportFormat.GLSP;
    }

    @Override
    /** Descripción: Ejecuta la operación «importMetamodel» correspondiente a la responsabilidad de TypeScriptType. Entradas: source: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ public MetamodelImportOutput importMetamodel(MetamodelImportSource source, MetamodelImportOptions options) {
        if(!archiveReader.isZip(source)) throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Eclipse GLSP import requires a project ZIP");
        List<ImportSourceEntry> entries = archiveReader.read(source, PROJECT_EXTENSIONS);
        ProjectMetadata metadata = inspect(entries);
        List<TransformationWarning> warnings = new ArrayList<>(metadata.warnings());
        MetamodelImportOutput semantic = semanticSource(entries, source.fileName(), options, warnings);
        MetaModel result = withGlspMetadata(semantic.metamodel(), source.fileName(), metadata);
        warnings.addAll(semantic.warnings());
        return new MetamodelImportOutput(result, semantic.diagram(), warnings);
    }

    /** Descripción: Ejecuta la operación «semanticSource» correspondiente a la responsabilidad de TypeScriptType. Entradas: entries: colección de elementos que debe procesarse; projectName: dato de entrada necesario para la operación; options: configuración que gobierna el comportamiento; warnings: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelImportOutput semanticSource(List<ImportSourceEntry> entries, String projectName, MetamodelImportOptions options,
            List<TransformationWarning> warnings) {
        List<ImportSourceEntry> ecore = filter(entries, ".ecore");
        if(!ecore.isEmpty()) {
            if(ecore.size() > 1) warnings.add(new TransformationWarning("IMPORT_GLSP_MULTIPLE_ECORE", "$", "Several Ecore source models were found; the first one was selected deterministically: " + ecore.getFirst().path()));
            if(hasAny(entries, ".java", ".ts", ".tsx", ".py")) warnings.add(new TransformationWarning("IMPORT_GLSP_GENERATED_CODE_IGNORED", "$", "An Ecore source model was preferred over generated or handwritten server code"));
            ImportSourceEntry selected = ecore.getFirst();
            return ecoreImporter.importMetamodel(new MetamodelImportSource(selected.path(), "application/xml", selected.content()), options);
        }
        List<ImportSourceEntry> javaSources = filter(entries, ".java");
        if(!javaSources.isEmpty()) {
            if(hasAny(entries, ".ts", ".tsx", ".py")) warnings.add(new TransformationWarning("IMPORT_GLSP_MULTIPLE_SERVER_LANGUAGES", "$", "Java was selected as the GLSP server semantic source; other language declarations remain provenance"));
            return javaImporter.importMetamodel(zipSource(projectName + ".java.zip", javaSources), options);
        }
        List<ImportSourceEntry> python = filter(entries, ".py");
        if(!python.isEmpty()) {
            if(hasAny(entries, ".ts", ".tsx")) warnings.add(new TransformationWarning("IMPORT_GLSP_MULTIPLE_SERVER_LANGUAGES", "$", "Python was selected as the GLSP semantic source; TypeScript declarations remain provenance"));
            return pythonImporter.importMetamodel(zipSource(projectName + ".python.zip", python), options);
        }
        List<ImportSourceEntry> typeScript = new ArrayList<>();
        typeScript.addAll(filter(entries, ".ts"));
        typeScript.addAll(filter(entries, ".tsx"));
        typeScript.sort(java.util.Comparator.comparing(ImportSourceEntry::path));
        if(!typeScript.isEmpty()) return new TypeScriptProjectBuilder(projectName, typeScript).build();
        throw new MetamodelDesignException(Reason.INVALID_REQUEST, "The GLSP project does not contain an Ecore, Java, Python or TypeScript semantic source that HYDRA can derive");
    }

    /** Descripción: Ejecuta la operación «inspect» correspondiente a la responsabilidad de TypeScriptType. Entradas: entries: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private ProjectMetadata inspect(List<ImportSourceEntry> entries) {
        Set<String> diagramTypes = new LinkedHashSet<>();
        Set<String> platforms = new LinkedHashSet<>();
        Set<String> modules = new LinkedHashSet<>();
        List<TransformationWarning> warnings = new ArrayList<>();
        boolean java = hasAny(entries, ".java") || entries.stream().anyMatch(entry -> leaf(entry.path()).equals("pom.xml") || leaf(entry.path()).endsWith(".gradle") || leaf(entry.path()).endsWith(".gradle.kts"));
        boolean typescript = hasAny(entries, ".ts", ".tsx");
        boolean python = hasAny(entries, ".py");
        for(ImportSourceEntry entry : entries) {
            modules.add(rootModule(entry.path()));
            String content = new String(entry.content(), StandardCharsets.UTF_8);
            String lower = content.toLowerCase(Locale.ROOT);
            if(lower.contains("@eclipse-glsp/vscode-integration") || lower.contains("vscode")) platforms.add("VS_CODE");
            if(lower.contains("@eclipse-glsp/theia-integration") || lower.contains("theia")) platforms.add("THEIA");
            if(lower.contains("org.eclipse.glsp.ide") || lower.contains("eclipse rcp")) platforms.add("ECLIPSE_DESKTOP");
            if(lower.contains("@eclipse-glsp/client") || lower.contains("glspclient")) platforms.add("STANDALONE_WEB");
            collectDiagramTypes(entry, content, diagramTypes);
        }
        if(platforms.isEmpty()) platforms.add("UNSPECIFIED");
        if(diagramTypes.isEmpty()) warnings.add(new TransformationWarning("IMPORT_GLSP_DIAGRAM_TYPE_UNKNOWN", "$", "No stable diagramType declaration was found; GLSP project metadata was preserved without inventing one"));
        String serverLanguage = java && typescript ? "JAVA_AND_TYPESCRIPT" : java ? "JAVA" : typescript ? "TYPESCRIPT" : python ? "PYTHON" : "UNKNOWN";
        return new ProjectMetadata(serverLanguage, List.copyOf(diagramTypes), List.copyOf(platforms), modules.stream().filter(value -> !value.isBlank()).sorted().toList(), List.copyOf(warnings));
    }

    /** Descripción: Ejecuta la operación «collectDiagramTypes» correspondiente a la responsabilidad de TypeScriptType. Entradas: entry: dato de entrada necesario para la operación; content: valor de entrada que debe procesarse; result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void collectDiagramTypes(ImportSourceEntry entry, String content, Set<String> result) {
        String leaf = leaf(entry.path());
        if(leaf.endsWith(".json")) {
            try {
                JsonNode root = objectMapper.readTree(entry.content());
                collectJsonField(root, "diagramType", result);
                collectJsonField(root, "diagramTypes", result);
            } catch(Exception ignored) {
                /* Non-contract JSON files do not invalidate the project import. */
            }
        }
        Pattern pattern = Pattern.compile("(?i)(?:diagramType|diagram_type)\\s*[:=]\\s*['\"]([^'\"]+)['\"]");
        Matcher matcher = pattern.matcher(content);
        while(matcher.find()) result.add(matcher.group(1).trim());
    }

    /** Descripción: Ejecuta la operación «collectJsonField» correspondiente a la responsabilidad de TypeScriptType. Entradas: node: dato de entrada necesario para la operación; field: dato de entrada necesario para la operación; result: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void collectJsonField(JsonNode node, String field, Set<String> result) {
        if(node == null || node.isMissingNode()) return;
        if(node.isObject()) {
            JsonNode value = node.get(field);
            if(value != null) {
                if(value.isTextual() && !value.asText().isBlank()) result.add(value.asText());
                else if(value.isArray()) for(JsonNode item : value) if(item.isTextual() && !item.asText().isBlank()) result.add(item.asText());
            }
            node.properties().forEach(entry -> collectJsonField(entry.getValue(), field, result));
        } else if(node.isArray()) for(JsonNode item : node) collectJsonField(item, field, result);
    }

    /** Descripción: Ejecuta la operación «withGlspMetadata» correspondiente a la responsabilidad de TypeScriptType. Entradas: source: dato de entrada necesario para la operación; projectName: dato de entrada necesario para la operación; metadata: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaModel withGlspMetadata(MetaModel source, String projectName, ProjectMetadata metadata) {
        Map<String, String> annotations = new LinkedHashMap<>(source.annotations());
        annotations.put("import:format", "glsp");
        annotations.put("import:originKind", "IMPORTED");
        annotations.put("import:source", projectName);
        annotations.put("glsp:serverLanguage", metadata.serverLanguage());
        annotations.put("glsp:clientLanguage", "TYPESCRIPT");
        annotations.put("glsp:diagramTypes", String.join(",", metadata.diagramTypes()));
        annotations.put("glsp:platforms", String.join(",", metadata.platforms()));
        annotations.put("glsp:modules", String.join(",", metadata.modules()));
        annotations.put("glsp:sourceModelBridgeRequired", "true");
        annotations.put("glsp:manualOperationHandlersPreserved", "true");
        annotations.put("roundTrip:preserveManualRegions", "true");
        return new MetaModel(source.schemaVersion(), source.id(), source.name(), source.classes(), source.enums(), source.dataTypes(), source.relations(), annotations);
    }

    /** Descripción: Ejecuta la operación «zipSource» correspondiente a la responsabilidad de TypeScriptType. Entradas: name: dato de entrada necesario para la operación; entries: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelImportSource zipSource(String name, List<ImportSourceEntry> entries) {
        try(ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream output = new ZipOutputStream(bytes)) {
            for(ImportSourceEntry entry : entries) {
                output.putNextEntry(new ZipEntry(entry.path()));
                output.write(entry.content());
                output.closeEntry();
            }
            output.finish();
            return new MetamodelImportSource(name, "application/zip", bytes.toByteArray());
        } catch(IOException exception) {
            throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Could not prepare GLSP source files for neutral import", exception);
        }
    }

    /** Descripción: Ejecuta la operación «filter» correspondiente a la responsabilidad de TypeScriptType. Entradas: entries: colección de elementos que debe procesarse; extension: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<ImportSourceEntry> filter(List<ImportSourceEntry> entries, String extension) {
        String lower = extension.toLowerCase(Locale.ROOT);
        return entries.stream().filter(entry -> entry.path().toLowerCase(Locale.ROOT).endsWith(lower)).sorted(java.util.Comparator.comparing(ImportSourceEntry::path)).toList();
    }

    /** Descripción: Evalúa la condición «hasAny» sin modificar el estado externo. Entradas: entries: colección de elementos que debe procesarse; extensions: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean hasAny(List<ImportSourceEntry> entries, String... extensions) {
        return entries.stream().map(ImportSourceEntry::path).map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(path -> java.util.Arrays.stream(extensions).map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(path::endsWith));
    }

    /** Descripción: Ejecuta la operación «rootModule» correspondiente a la responsabilidad de TypeScriptType. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String rootModule(String path) {
        String normalized = path.replace('\\', '/');
        int separator = normalized.indexOf('/');
        return separator < 0 ? "" : normalized.substring(0, separator);
    }

    /** Descripción: Ejecuta la operación «leaf» correspondiente a la responsabilidad de TypeScriptType. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String leaf(String path) {
        String normalized = path.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        return separator < 0 ? normalized : normalized.substring(separator + 1);
    }

    private record ProjectMetadata(String serverLanguage, List<String> diagramTypes, List<String> platforms, List<String> modules,
            List<TransformationWarning> warnings) { }

    /** Conserva las declaraciones estructurales TypeScript sin ejecutar bundlers ni código. */
    private static final class TypeScriptProjectBuilder {
        private static final Pattern TYPE = Pattern.compile("(?m)(?:export\\s+)?(?:(abstract)\\s+)?(interface|class|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)(?:\\s+extends\\s+([^\\{]+))?\\s*\\{");
        private static final Pattern FIELD = Pattern.compile("(?m)^\\s*(?:readonly\\s+)?([A-Za-z_$][A-Za-z0-9_$]*)(\\?)?\\s*:\\s*([^;\\n]+);?");
        private static final Pattern METHOD = Pattern.compile("(?m)^\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(([^)]*)\\)\\s*:\\s*([^;\\n]+);?");
        private final String projectName;
        private final List<TypeScriptType> types = new ArrayList<>();
        private final List<TransformationWarning> warnings = new ArrayList<>();
        private final Map<String, String> idByName = new LinkedHashMap<>();

        /** Descripción: Inicializa una instancia de TypeScriptProjectBuilder con sus dependencias y datos de entrada. Entradas: projectName: dato de entrada necesario para la operación; sources: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private TypeScriptProjectBuilder(String projectName, List<ImportSourceEntry> sources) {
            this.projectName = projectName;
            for(ImportSourceEntry source : sources) parse(source);
            for(TypeScriptType type : types) idByName.putIfAbsent(type.name(), ImportParsingSupport.safeId(type.name(), "type"));
        }

        /** Descripción: Crea o prepara el resultado de «build» dentro de TypeScriptType. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ private MetamodelImportOutput build() {
            List<MetaClass> classes = new ArrayList<>();
            List<MetaEnum> enums = new ArrayList<>();
            List<MetaRelation> relations = new ArrayList<>();
            for(TypeScriptType type : types) {
                if(type.kind().equals("enum")) enums.add(buildEnum(type));
                else classes.add(buildClass(type, relations));
            }
            if(classes.isEmpty() && enums.isEmpty()) throw new MetamodelDesignException(Reason.INVALID_REQUEST, "No TypeScript class, interface or enum declarations could be derived from the GLSP project");
            String base = ImportParsingSupport.fileBaseName(projectName);
            Map<String, String> annotations = new LinkedHashMap<>();
            annotations.put("import:format", "typescript");
            annotations.put("import:source", projectName);
            annotations.put("roundTrip:preserveManualRegions", "true");
            MetaModel model = new MetaModel(MetaModel.CURRENT_SCHEMA_VERSION, ImportParsingSupport.safeId(base, "glsp-model"), ImportParsingSupport.pascal(base, "GlspModel"), classes, enums, List.of(), relations, annotations);
            return new MetamodelImportOutput(model, null, warnings);
        }

        /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de TypeScriptType. Entradas: source: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void parse(ImportSourceEntry source) {
            String text = new String(source.content(), StandardCharsets.UTF_8);
            Matcher matcher = TYPE.matcher(text);
            int searchFrom = 0;
            while(matcher.find(searchFrom)) {
                int openingBrace = matcher.end() - 1;
                int closingBrace = matchingBrace(text, openingBrace);
                if(closingBrace < 0) {
                    warnings.add(new TransformationWarning("IMPORT_GLSP_TYPESCRIPT_UNCLOSED_TYPE", source.path(), "Type declaration has no matching closing brace: " + matcher.group(3)));
                    break;
                }
                String kind = matcher.group(2);
                String name = matcher.group(3);
                List<String> parents = split(matcher.group(4));
                String body = text.substring(openingBrace + 1, closingBrace);
                List<TypeScriptField> fields = new ArrayList<>();
                List<TypeScriptMethod> methods = new ArrayList<>();
                List<String> literals = new ArrayList<>();
                if(kind.equals("enum")) {
                    for(String item : split(body)) {
                        String literal = item.trim().replaceFirst("\\s*=.*$", "");
                        if(literal.matches("[A-Za-z_$][A-Za-z0-9_$]*")) literals.add(literal);
                    }
                } else {
                    String normalizedBody = body.replace(";", ";\n");
                    Matcher field = FIELD.matcher(normalizedBody);
                    while(field.find()) fields.add(new TypeScriptField(field.group(1), field.group(3).trim(), field.group(2) != null));
                    Matcher method = METHOD.matcher(normalizedBody);
                    while(method.find()) methods.add(new TypeScriptMethod(method.group(1), method.group(2), method.group(3).trim()));
                }
                types.add(new TypeScriptType(kind, name, matcher.group(1) != null, source.path(), parents, fields, methods, literals));
                searchFrom = closingBrace + 1;
            }
        }

        /** Descripción: Ejecuta la operación «matchingBrace» correspondiente a la responsabilidad de TypeScriptType. Entradas: text: valor de entrada que debe procesarse; openingBrace: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int matchingBrace(String text, int openingBrace) {
            int depth = 0;
            char quote = 0;
            boolean lineComment = false;
            boolean blockComment = false;
            for(int index = openingBrace; index < text.length(); index++) {
                char current = text.charAt(index);
                char next = index + 1 < text.length() ? text.charAt(index + 1) : 0;
                if(lineComment) {
                    if(current == '\n') lineComment = false;
                    continue;
                }
                if(blockComment) {
                    if(current == '*' && next == '/') { blockComment = false; index++; }
                    continue;
                }
                if(quote != 0) {
                    if(current == quote && (index == 0 || text.charAt(index - 1) != '\\')) quote = 0;
                    continue;
                }
                if(current == '/' && next == '/') { lineComment = true; index++; continue; }
                if(current == '/' && next == '*') { blockComment = true; index++; continue; }
                if(current == '\'' || current == '"' || current == '`') { quote = current; continue; }
                if(current == '{') depth++;
                else if(current == '}' && --depth == 0) return index;
            }
            return -1;
        }

        /** Descripción: Crea o prepara el resultado de «buildEnum» dentro de TypeScriptType. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaEnum buildEnum(TypeScriptType type) {
            String id = idByName.get(type.name());
            List<MetaEnumLiteral> literals = new ArrayList<>();
            for(int index = 0; index < type.literals().size(); index++) {
                String literal = type.literals().get(index);
                literals.add(new MetaEnumLiteral(ImportParsingSupport.literalId(id, literal), literal, index, Map.of("typescript:sourcePath", type.sourcePath())));
            }
            return new MetaEnum(id, type.name(), literals, Map.of("typescript:sourcePath", type.sourcePath()));
        }

        /** Descripción: Crea o prepara el resultado de «buildClass» dentro de TypeScriptType. Entradas: type: dato de entrada necesario para la operación; relations: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClass buildClass(TypeScriptType type, List<MetaRelation> relations) {
            String classId = idByName.get(type.name());
            List<MetaAttribute> attributes = new ArrayList<>();
            List<MetaOperation> operations = new ArrayList<>();
            for(TypeScriptField field : type.fields()) {
                TypeUse use = typeUse(field.type(), field.optional());
                String known = knownType(use.baseType());
                if(!known.isBlank() && types.stream().filter(item -> item.name().equals(known)).noneMatch(item -> item.kind().equals("enum"))) {
                    relations.add(new MetaRelation(ImportParsingSupport.relationId(classId, field.name()), field.name(), classId, idByName.get(known), MetaRelationKind.REFERENCE, null, use.multiplicity(), "", use.ordered(), use.unique(), Map.of("typescript:genericType", field.type())));
                } else {
                    attributes.add(new MetaAttribute(ImportParsingSupport.attributeId(classId, field.name()), field.name(), reference(use.baseType(), known), use.multiplicity(), use.ordered(), use.unique(), "", Map.of("typescript:genericType", field.type())));
                }
            }
            for(TypeScriptMethod method : type.methods()) operations.add(operation(classId, method));
            for(String parent : type.parents()) {
                String known = knownType(parent.replaceAll("<.*>", "").trim());
                if(known.isBlank()) warnings.add(new TransformationWarning("IMPORT_GLSP_TYPESCRIPT_EXTERNAL_SUPERTYPE", "$.classes[" + classId + "]", "External TypeScript supertype remains provenance: " + parent));
                else {
                    TypeScriptType parentType = types.stream().filter(item -> item.name().equals(known)).findFirst().orElse(null);
                    MetaRelationKind relationKind = type.kind().equals("class") && parentType != null && effectiveKind(parentType) == MetaClassKind.INTERFACE ? MetaRelationKind.IMPLEMENTATION : MetaRelationKind.INHERITANCE;
                    relations.add(new MetaRelation("supertype:" + classId + ":" + idByName.get(known), "", classId, idByName.get(known), relationKind, null, null, "", true, true, Map.of("typescript:sourceType", parent)));
                }
            }
            MetaClassKind kind = effectiveKind(type);
            Map<String, String> annotations = new LinkedHashMap<>();
            annotations.put("typescript:sourcePath", type.sourcePath());
            annotations.put("typescript:kind", type.kind());
            if(type.kind().equals("interface") && kind != MetaClassKind.INTERFACE) {
                annotations.put("typescript:declaredInterface", "true");
                annotations.put("hydra:semanticAdaptation", "STRUCTURAL_INTERFACE_TO_ABSTRACT_CLASS");
                warnings.add(new TransformationWarning("IMPORT_GLSP_STRUCTURAL_INTERFACE_ADAPTED", "$.classes[" + classId + "]", "TypeScript structural interface was represented as an abstract class because HYDRA structural relations cannot target interfaces"));
            }
            return new MetaClass(classId, type.name(), kind, attributes, operations, annotations);
        }

        /** Descripción: Ejecuta la operación «effectiveKind» correspondiente a la responsabilidad de TypeScriptType. Entradas: type: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaClassKind effectiveKind(TypeScriptType type) {
            if(type.kind().equals("interface")) return type.fields().isEmpty() ? MetaClassKind.INTERFACE : MetaClassKind.ABSTRACT_CLASS;
            return type.abstractType() ? MetaClassKind.ABSTRACT_CLASS : MetaClassKind.CLASS;
        }

        /** Descripción: Ejecuta la operación «operation» correspondiente a la responsabilidad de TypeScriptType. Entradas: classId: identificador que delimita el recurso objetivo; method: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaOperation operation(String classId, TypeScriptMethod method) {
            String operationId = "operation:" + classId + ":" + ImportParsingSupport.safeId(method.name(), "operation");
            List<MetaParameter> parameters = new ArrayList<>();
            for(String parameter : split(method.parameters())) {
                String[] pieces = parameter.split(":", 2);
                String name = pieces[0].trim().replace("?", "");
                String type = pieces.length > 1 ? pieces[1].trim().replaceFirst("\\s*=.*$", "") : "unknown";
                TypeUse use = typeUse(type, pieces[0].contains("?"));
                String known = knownType(use.baseType());
                parameters.add(new MetaParameter("parameter:" + operationId + ":" + ImportParsingSupport.safeId(name, "parameter"), name, reference(use.baseType(), known), use.multiplicity(), use.ordered(), use.unique(), Map.of("typescript:genericType", type)));
            }
            TypeUse result = typeUse(method.returnType(), false);
            boolean voidType = Set.of("void", "never").contains(result.baseType());
            String known = voidType ? "" : knownType(result.baseType());
            return new MetaOperation(operationId, method.name(), voidType ? null : reference(result.baseType(), known), voidType ? null : result.multiplicity(), result.ordered(), result.unique(), parameters, List.of(), MetaOperationBehaviorKind.UNSPECIFIED, Map.of("typescript:genericReturnType", method.returnType(), "typescript:bodyImported", "false"));
        }

        /** Descripción: Ejecuta la operación «typeUse» correspondiente a la responsabilidad de TypeScriptType. Entradas: raw: dato de entrada necesario para la operación; explicitlyOptional: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private TypeUse typeUse(String raw, boolean explicitlyOptional) {
            String value = raw == null ? "unknown" : raw.trim();
            boolean optional = explicitlyOptional || value.matches(".*(?:^|\\|)\\s*(null|undefined)\\s*(?:\\||$).*");
            value = value.replaceAll("\\s*\\|\\s*(?:null|undefined)", "").replaceAll("(?:null|undefined)\\s*\\|\\s*", "").trim();
            Matcher array = Pattern.compile("(?:Readonly)?Array<(.+)>").matcher(value);
            if(array.matches()) return new TypeUse(array.group(1).trim(), Multiplicity.many(), true, !value.startsWith("Readonly"));
            if(value.endsWith("[]")) return new TypeUse(value.substring(0, value.length() - 2).trim(), Multiplicity.many(), true, true);
            return new TypeUse(value, optional ? Multiplicity.optional() : Multiplicity.one(), true, true);
        }

        /** Descripción: Ejecuta la operación «reference» correspondiente a la responsabilidad de TypeScriptType. Entradas: value: valor de entrada que debe procesarse; known: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private MetaTypeReference reference(String value, String known) {
            if(!known.isBlank()) {
                boolean enumType = types.stream().anyMatch(item -> item.name().equals(known) && item.kind().equals("enum"));
                return enumType ? MetaTypeReference.enumType(idByName.get(known)) : MetaTypeReference.classType(idByName.get(known));
            }
            String simple = value.replaceAll(".*\\.", "");
            return switch(simple) {
                case "string", "String" -> MetaTypeReference.primitive(MetaPrimitiveType.STRING);
                case "boolean", "Boolean" -> MetaTypeReference.primitive(MetaPrimitiveType.BOOLEAN);
                case "number", "Number" -> MetaTypeReference.primitive(MetaPrimitiveType.DOUBLE);
                case "bigint" -> MetaTypeReference.primitive(MetaPrimitiveType.LONG);
                case "Date" -> MetaTypeReference.primitive(MetaPrimitiveType.DATE);
                case "Uint8Array", "ArrayBuffer" -> MetaTypeReference.primitive(MetaPrimitiveType.BINARY);
                case "any", "unknown", "object" -> MetaTypeReference.primitive(MetaPrimitiveType.OBJECT);
                default -> MetaTypeReference.external(value);
            };
        }

        /** Descripción: Ejecuta la operación «knownType» correspondiente a la responsabilidad de TypeScriptType. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String knownType(String value) {
            String clean = value.replaceAll("<.*>", "").replaceAll(".*\\.", "").trim();
            return idByName.containsKey(clean) ? clean : "";
        }

        /** Descripción: Ejecuta la operación «split» correspondiente a la responsabilidad de TypeScriptType. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static List<String> split(String value) {
            if(value == null || value.isBlank()) return List.of();
            List<String> result = new ArrayList<>();
            int depth = 0;
            int start = 0;
            for(int index = 0; index < value.length(); index++) {
                char current = value.charAt(index);
                if(current == '<' || current == '(' || current == '[' || current == '{') depth++;
                else if(current == '>' || current == ')' || current == ']' || current == '}') depth--;
                else if(current == ',' && depth == 0) {
                    result.add(value.substring(start, index).trim());
                    start = index + 1;
                }
            }
            result.add(value.substring(start).trim());
            return result.stream().filter(item -> !item.isBlank()).toList();
        }

        private record TypeScriptType(String kind, String name, boolean abstractType, String sourcePath, List<String> parents,
                List<TypeScriptField> fields, List<TypeScriptMethod> methods, List<String> literals) { }
        private record TypeScriptField(String name, String type, boolean optional) { }
        private record TypeScriptMethod(String name, String parameters, String returnType) { }
        private record TypeUse(String baseType, Multiplicity multiplicity, boolean ordered, boolean unique) { }
    }
}
