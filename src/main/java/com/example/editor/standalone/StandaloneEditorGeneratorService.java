package com.example.editor.standalone;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.example.editor.portability.CapabilitySelection;
import com.example.editor.portability.HydraCapabilityKind;
import com.example.editor.portability.HydraCapabilityPackage;
import com.example.editor.portability.archive.CapabilityPackageArchiveResult;
import com.example.editor.portability.archive.CapabilityPackageArchiveService;
import com.example.editor.grammar.runtime.GrammarCompilationSnapshot;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.SerializationFeature;

/** Genera una aplicación web local que edita modelos sin incluir el entorno de diseño de HYDRA. */
@Service
public class StandaloneEditorGeneratorService {
    private static final String TEMPLATE_ROOT = "standalone-template/";
    private final StandaloneEditorGenerationProperties properties;
    private final CapabilityPackageArchiveService packages;
    private final ObjectWriter writer;

    /** Descripción: Inicializa una instancia de StandaloneEditorGeneratorService con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento; packages: dato de entrada necesario para la operación; mapper: componente de conversión utilizado durante el procesamiento. Salida: deja la instancia inicializada y lista para su uso. */ public StandaloneEditorGeneratorService(StandaloneEditorGenerationProperties properties,
            CapabilityPackageArchiveService packages, ObjectMapper mapper) {
        this.properties = properties;
        this.packages = packages;
        this.writer = mapper.writer(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    /** Descripción: Crea o prepara el resultado de «generate» dentro de StandaloneEditorGeneratorService. Entradas: request: solicitud con los datos necesarios para ejecutar la operación. Salida: devuelve el resultado definido por el contrato del método. */ public StandaloneEditorGenerationResult generate(StandaloneEditorGenerationRequest request) {
        if(!properties.isEnabled()) throw error(StandaloneEditorGenerationException.Reason.DISABLED,
                "Standalone editor generation is disabled");
        if(request == null || request.capabilityPackage() == null) throw error(
                StandaloneEditorGenerationException.Reason.INVALID_REQUEST,
                "A HydraCapabilityPackage is required");
        String applicationId = identifier(request.applicationId());
        String editorId = required(request.editorId(), "Editor id");
        HydraCapabilityPackage capabilityPackage = request.capabilityPackage();
        EditorSelection selectedEditor = editorSelection(capabilityPackage, editorId);
        CapabilitySelection editorSelection = selectedEditor.editor();
        String semanticScopeId = required(capabilityPackage.manifest().semanticScopeId(), "Semantic scope id");
        String metamodelId = request.metamodelId().isBlank() ? capabilityPackage.manifest().metamodelId() : request.metamodelId();
        CapabilitySelection metamodelSelection = optionalSelection(capabilityPackage, HydraCapabilityKind.METAMODEL);
        if(!selectedEditor.textual()) {
            metamodelId = required(metamodelId, "Metamodel id");
            if(!metamodelId.equals(capabilityPackage.manifest().metamodelId())) throw error(
                    StandaloneEditorGenerationException.Reason.INVALID_REQUEST,
                    "The package belongs to another metamodel");
            if(metamodelSelection == null) throw error(StandaloneEditorGenerationException.Reason.INVALID_REQUEST,
                    "Visual standalone editor requires capability: METAMODEL");
            requireDocument(capabilityPackage, metamodelSelection);
        } else if(!request.metamodelId().isBlank() && !request.metamodelId().equals(capabilityPackage.manifest().metamodelId())) {
            throw error(StandaloneEditorGenerationException.Reason.INVALID_REQUEST,
                    "The requested metamodel does not match the package");
        }
        requireDocument(capabilityPackage, editorSelection);
        if(selectedEditor.grammar() != null) requireDocument(capabilityPackage, selectedEditor.grammar());
        if(selectedEditor.syntaxStyle() != null) requireDocument(capabilityPackage, selectedEditor.syntaxStyle());
        validateTextualSnapshot(capabilityPackage, selectedEditor, request.grammarSnapshot());

        CapabilityPackageArchiveResult packageArchive = packages.exportPackage(capabilityPackage, true);
        String packagePath = request.includeCapabilityArchive()
                ? "capabilities/" + packageArchive.fileName() : "";
        int port = request.port() <= 0 ? properties.getDefaultPort() : request.port();
        long maximumModelBytes = Math.min(request.maximumModelBytes(),
                properties.getMaximumGeneratedModelBytes());
        String runtimeBaseUrl = request.runtimeBaseUrl().isBlank()
                ? properties.getDefaultRuntimeBaseUrl() : request.runtimeBaseUrl();
        String metamodelPath = metamodelSelection == null ? "" : canonicalPath(metamodelSelection.documentId());
        String editorPath = canonicalPath(editorSelection.documentId());
        List<String> capabilities = capabilityPackage.manifest().capabilities().stream()
                .map(CapabilitySelection::kind).map(Enum::name).distinct().sorted().toList();
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("maximumModelBytes", Long.toString(maximumModelBytes));
        settings.put("includeAllCanonicalDocuments", Boolean.toString(request.includeAllCanonicalDocuments()));
        settings.put("includedViewpointIds", String.join(",", request.includedViewpointIds()));
        settings.put("allowedOperationIds", String.join(",", request.allowedOperationIds()));
        settings.put("deploymentTargets", request.deploymentTargets().stream().map(Enum::name).sorted().reduce((a, b) -> a + "," + b).orElse("SOURCE"));
        settings.put("editorKind", selectedEditor.textual() ? "TEXTUAL" : "VISUAL");
        if(selectedEditor.textual()) {
            settings.put("grammarDocumentPath", "frontend/" + canonicalPath(selectedEditor.grammar().documentId()));
            settings.put("textualSyntaxStyleDocumentPath", selectedEditor.syntaxStyle() == null ? "" : "frontend/" + canonicalPath(selectedEditor.syntaxStyle().documentId()));
            settings.put("textualRuntimeAvailability", textualRuntimeAvailability(request.grammarSnapshot()));
            settings.put("compilationSnapshotId", textualDefinitionField(capabilityPackage, editorSelection, "compilationSnapshotId"));
            if(request.grammarSnapshot() != null) {
                settings.put("providerId", request.grammarSnapshot().providerId());
                settings.put("toolchainProfileId", request.grammarSnapshot().toolchainProfileId());
                settings.put("browserWorkerEntry", bundledRuntimePath(request.grammarSnapshot()));
            }
        }
        settings.putAll(request.branding());
        StandaloneEditorDescriptor descriptor = new StandaloneEditorDescriptor(
                StandaloneEditorDescriptor.CURRENT_SCHEMA_VERSION, applicationId,
                request.name().isBlank() ? applicationId : request.name(), metamodelId, semanticScopeId, editorId,
                port, request.runtimeMode(), runtimeBaseUrl, packagePath,
                packageArchive.descriptor().contentHash(), metamodelPath.isBlank() ? "" : "frontend/" + metamodelPath,
                "frontend/" + editorPath, capabilities, settings,
                Map.of("generatedBy", "HYDRA", "delivery", selectedEditor.textual() ? "STANDALONE_TEXTUAL_SOURCE" : "STANDALONE_WEB_SOURCE",
                        "designerIncluded", "false", "embeddedPersistence", "true"));

        Map<String, byte[]> entries = entries(descriptor, capabilityPackage, packageArchive,
                request.includeCapabilityArchive(), request.includeAllCanonicalDocuments(),
                metamodelSelection, selectedEditor, request.grammarSnapshot(), maximumModelBytes, request.deploymentTargets());
        byte[] archive = zip(entries);
        if(archive.length > properties.getMaximumArchiveBytes()) throw error(
                StandaloneEditorGenerationException.Reason.LIMIT_EXCEEDED,
                "Generated standalone editor exceeds the configured archive limit");
        return new StandaloneEditorGenerationResult(applicationId + archiveSuffix(request.deploymentTargets()), archive,
                sha256(archive), descriptor, entries.keySet().stream().sorted().toList());
    }

    /** Descripción: Ejecuta la operación «entries» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: descriptor: dato de entrada necesario para la operación; capabilityPackage: dato de entrada necesario para la operación; packageArchive: archivo o contenido que debe procesarse; includePackage: dato de entrada necesario para la operación; includeAllDocuments: dato de entrada necesario para la operación; metamodelSelection: dato de entrada necesario para la operación; editorSelection: dato de entrada necesario para la operación; maximumModelBytes: dato de entrada necesario para la operación; deploymentTargets: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private Map<String, byte[]> entries(StandaloneEditorDescriptor descriptor,
            HydraCapabilityPackage capabilityPackage, CapabilityPackageArchiveResult packageArchive,
            boolean includePackage, boolean includeAllDocuments, CapabilitySelection metamodelSelection,
            EditorSelection selectedEditor, GrammarCompilationSnapshot grammarSnapshot, long maximumModelBytes, Set<StandaloneDeploymentTarget> deploymentTargets) {
        CapabilitySelection editorSelection = selectedEditor.editor();
        Map<String, byte[]> result = new TreeMap<>();
        put(result, "standalone-editor.json", json(descriptor));
        put(result, "frontend/index.html", text(template("frontend/index.html.template")
                .replace("__APPLICATION_NAME_HTML__", escapeHtml(descriptor.name()))));
        put(result, "frontend/app.js", templateBytes(selectedEditor.textual() ? "frontend/textual-app.js" : "frontend/app.js"));
        put(result, "frontend/runtime.js", templateBytes("frontend/runtime.js"));
        if(!selectedEditor.textual()) put(result, "frontend/visual-runtime.js", templateBytes("frontend/visual-runtime.js"));
        put(result, "frontend/styles.css", templateBytes("frontend/styles.css"));
        put(result, "frontend/config/capabilities.json", json(capabilityIndex(capabilityPackage)));
        put(result, "backend/src/HydraStandaloneServer.java", text(template(
                "backend/src/HydraStandaloneServer.java.template")
                .replace("__MAX_MODEL_BYTES__", Long.toString(maximumModelBytes))
                .replace("__DEFAULT_PORT__", Integer.toString(descriptor.port()))
                .replace("__APPLICATION_ID_JSON__", jsonString(descriptor.applicationId()))));
        addCanonicalDocuments(result, capabilityPackage, includeAllDocuments, requiredStandaloneDocuments(metamodelSelection, selectedEditor));
        if(selectedEditor.textual() && grammarSnapshot != null) addTextualRuntimeSources(result, grammarSnapshot);
        capabilityPackage.resources().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> put(result, "frontend/resources/" + safePath(entry.getKey()),
                        resourceBytes(entry.getValue())));
        put(result, "README.md", text(readme(descriptor)));
        put(result, "build.cmd", text("@echo off\r\nif not exist backend\\out mkdir backend\\out\r\njavac --release 21 -d backend\\out backend\\src\\HydraStandaloneServer.java\r\n"));
        put(result, "run.cmd", text("@echo off\r\ncall build.cmd || exit /b 1\r\njava -cp backend\\out HydraStandaloneServer --root=\"%~dp0\" --port=" + descriptor.port() + "\r\n"));
        put(result, "build.sh", text("#!/usr/bin/env sh\nset -eu\nmkdir -p backend/out\njavac --release 21 -d backend/out backend/src/HydraStandaloneServer.java\n"));
        put(result, "run.sh", text("#!/usr/bin/env sh\nset -eu\ncd \"$(dirname \"$0\")\"\n./build.sh\njava -cp backend/out HydraStandaloneServer --root=\"$(pwd)\" --port=" + descriptor.port() + "\n"));
        put(result, "verify-package.txt", text("contentHash=" + packageArchive.descriptor().contentHash()
                + "\narchiveSha256=" + packageArchive.archiveSha256() + "\n"));
        put(result, "models/.gitkeep", new byte[0]);
        if(includePackage) put(result, "capabilities/" + packageArchive.fileName(),
                packageArchive.archive());
        addDeploymentAssets(result, descriptor, deploymentTargets);
        if(result.size() > properties.getMaximumEntries()) throw error(
                StandaloneEditorGenerationException.Reason.LIMIT_EXCEEDED,
                "Generated standalone editor contains too many files");
        return result;
    }

    /** Descripción: Ejecuta la operación «capabilityIndex» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<Map<String, String>> capabilityIndex(HydraCapabilityPackage value) {
        return value.manifest().capabilities().stream().sorted(Comparator.comparing(CapabilitySelection::id))
                .map(selection -> Map.of(
                        "id", selection.id(),
                        "kind", selection.kind().name(),
                        "documentId", selection.documentId(),
                        "path", "/" + canonicalPath(selection.documentId()),
                        "schemaVersion", selection.schemaVersion(),
                        "executionMode", selection.executionMode().name()))
                .toList();
    }

    /** Descripción: Ejecuta la operación «archiveSuffix» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: targets: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String archiveSuffix(Set<StandaloneDeploymentTarget> targets) {
        boolean docker = targets.contains(StandaloneDeploymentTarget.DOCKER);
        boolean kubernetes = targets.contains(StandaloneDeploymentTarget.KUBERNETES);
        if(docker && kubernetes) return "-containers.zip";
        if(kubernetes) return "-kubernetes.zip";
        if(docker) return "-docker.zip";
        return "-standalone.zip";
    }

    /** Descripción: Ejecuta la operación «addDeploymentAssets» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: result: dato de entrada necesario para la operación; descriptor: dato de entrada necesario para la operación; targets: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addDeploymentAssets(Map<String, byte[]> result, StandaloneEditorDescriptor descriptor,
            Set<StandaloneDeploymentTarget> targets) {
        boolean docker = targets.contains(StandaloneDeploymentTarget.DOCKER);
        boolean kubernetes = targets.contains(StandaloneDeploymentTarget.KUBERNETES);
        if(!docker && !kubernetes) return;
        put(result, "Dockerfile", text(dockerfile(descriptor)));
        put(result, ".dockerignore", text("backend/out\nmodels/*.model.json\n.git\n.DS_Store\n"));
        put(result, "CONTAINER_README.md", text(containerReadme(descriptor, docker, kubernetes)));
        if(docker) {
            put(result, "docker-compose.yml", text(dockerCompose(descriptor)));
            put(result, "start-docker.cmd", text("@echo off\r\ndocker compose up --build -d || exit /b 1\r\ntimeout /t 2 /nobreak >nul\r\nstart \"\" http://127.0.0.1:" + descriptor.port() + "\r\n"));
            put(result, "start-docker.sh", text("#!/usr/bin/env sh\nset -eu\ndocker compose up --build -d\nsleep 2\n(xdg-open http://127.0.0.1:" + descriptor.port() + " 2>/dev/null || open http://127.0.0.1:" + descriptor.port() + " 2>/dev/null || true)\n"));
        }
        if(kubernetes) {
            put(result, "deploy/kubernetes/deployment.yaml", text(kubernetesDeployment(descriptor)));
            put(result, "deploy/kubernetes/service.yaml", text(kubernetesService(descriptor)));
            put(result, "deploy/kubernetes/pvc.yaml", text(kubernetesPvc(descriptor)));
        }
    }

    /** Descripción: Ejecuta la operación «dockerfile» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: descriptor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String dockerfile(StandaloneEditorDescriptor descriptor) {
        return """
                FROM eclipse-temurin:21-jdk AS build
                WORKDIR /build
                COPY backend/src/HydraStandaloneServer.java backend/src/HydraStandaloneServer.java
                RUN mkdir -p backend/out && javac --release 21 -d backend/out backend/src/HydraStandaloneServer.java

                FROM eclipse-temurin:21-jre
                WORKDIR /opt/hydra-editor
                COPY --from=build /build/backend/out backend/out
                COPY frontend frontend
                COPY standalone-editor.json standalone-editor.json
                RUN mkdir -p models
                VOLUME ["/opt/hydra-editor/models"]
                EXPOSE %d
                ENTRYPOINT ["java","-cp","backend/out","HydraStandaloneServer","--root=/opt/hydra-editor","--host=0.0.0.0","--port=%d"]
                """.formatted(descriptor.port(), descriptor.port());
    }

    /** Descripción: Ejecuta la operación «dockerCompose» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: descriptor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String dockerCompose(StandaloneEditorDescriptor descriptor) {
        String service = containerName(descriptor.applicationId());
        return """
                services:
                  model-editor:
                    build: .
                    image: hydra/%s:latest
                    container_name: %s
                    ports:
                      - "127.0.0.1:%d:%d"
                    volumes:
                      - ./models:/opt/hydra-editor/models
                    restart: unless-stopped
                """.formatted(service, service, descriptor.port(), descriptor.port());
    }

    /** Descripción: Ejecuta la operación «kubernetesDeployment» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: descriptor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String kubernetesDeployment(StandaloneEditorDescriptor descriptor) {
        String name = containerName(descriptor.applicationId());
        return """
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  name: %s
                spec:
                  replicas: 1
                  selector:
                    matchLabels:
                      app: %s
                  template:
                    metadata:
                      labels:
                        app: %s
                    spec:
                      containers:
                        - name: editor
                          image: hydra/%s:latest
                          imagePullPolicy: IfNotPresent
                          ports:
                            - containerPort: %d
                          readinessProbe:
                            httpGet:
                              path: /api/health
                              port: %d
                            initialDelaySeconds: 2
                            periodSeconds: 5
                          livenessProbe:
                            httpGet:
                              path: /api/health
                              port: %d
                            initialDelaySeconds: 10
                            periodSeconds: 15
                          volumeMounts:
                            - name: models
                              mountPath: /opt/hydra-editor/models
                      volumes:
                        - name: models
                          persistentVolumeClaim:
                            claimName: %s-models
                """.formatted(name, name, name, name, descriptor.port(), descriptor.port(),
                descriptor.port(), name);
    }

    /** Descripción: Ejecuta la operación «kubernetesService» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: descriptor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String kubernetesService(StandaloneEditorDescriptor descriptor) {
        String name = containerName(descriptor.applicationId());
        return """
                apiVersion: v1
                kind: Service
                metadata:
                  name: %s
                spec:
                  selector:
                    app: %s
                  ports:
                    - name: http
                      port: 80
                      targetPort: %d
                  type: ClusterIP
                """.formatted(name, name, descriptor.port());
    }

    /** Descripción: Ejecuta la operación «kubernetesPvc» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: descriptor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String kubernetesPvc(StandaloneEditorDescriptor descriptor) {
        String name = containerName(descriptor.applicationId());
        return """
                apiVersion: v1
                kind: PersistentVolumeClaim
                metadata:
                  name: %s-models
                spec:
                  accessModes:
                    - ReadWriteOnce
                  resources:
                    requests:
                      storage: 1Gi
                """.formatted(name);
    }

    /** Descripción: Ejecuta la operación «containerReadme» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: descriptor: dato de entrada necesario para la operación; docker: dato de entrada necesario para la operación; kubernetes: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String containerReadme(StandaloneEditorDescriptor descriptor, boolean docker,
            boolean kubernetes) {
        StringBuilder value = new StringBuilder();
        value.append("# Container deployment\n\n");
        value.append("This image contains only the generated model editor and its portable HYDRA capabilities; the HYDRA designer is not included.\n\n");
        if(docker) value.append("## Docker\n\n`docker compose up --build` then open `http://localhost:")
                .append(descriptor.port()).append("`. Models are persisted in `./models`.\n\n");
        if(kubernetes) value.append("## Kubernetes\n\nBuild the image with `docker build -t hydra/")
                .append(containerName(descriptor.applicationId())).append(":latest .`, make it available to the cluster, then run `kubectl apply -f deploy/kubernetes/`.\n")
                .append("Use `kubectl port-forward service/").append(containerName(descriptor.applicationId()))
                .append(" 8080:80` and open `http://localhost:8080`.\n\n");
        if(isTextual(descriptor)) {
            value.append("This is a textual standalone distribution pinned to grammar snapshot `")
                    .append(descriptor.settings().getOrDefault("compilationSnapshotId", ""))
                    .append("`. Runtime availability is `")
                    .append(descriptor.settings().getOrDefault("textualRuntimeAvailability", "BUILD_REQUIRED"))
                    .append("`. A source-only or language-server-host distribution must not be treated as an offline semantic runtime until the required generated host has been built/provided.\n");
        } else {
            value.append("The embedded visual runtime loads the portable metamodel and editor layers carried by the capability package.\n");
        }
        return value.toString();
    }

    /** Descripción: Ejecuta la operación «containerName» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String containerName(String value) {
        String name = value.toLowerCase().replaceAll("[^a-z0-9.-]", "-")
                .replaceAll("^[^a-z0-9]+|[^a-z0-9]+$", "").replaceAll("-+", "-");
        return name.isBlank() ? "hydra-editor" : name;
    }

    /** Descripción: Añade al paquete standalone los documentos canónicos seleccionados de forma determinista. Entradas: result: mapa de archivos de salida; capabilityPackage: paquete canónico de capacidades; includeAllDocuments: indica si deben incluirse todos los documentos; requiredDocuments: identificadores requeridos. Salida: no devuelve valor; incorpora únicamente los documentos permitidos al resultado. */ private void addCanonicalDocuments(Map<String, byte[]> result,
            HydraCapabilityPackage capabilityPackage, boolean includeAllDocuments, List<String> requiredDocuments) {
        capabilityPackage.canonicalDocuments().entrySet().stream()
                .filter(entry -> includeAllDocuments || requiredDocuments.contains(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> put(result, "frontend/" + canonicalPath(entry.getKey()), json(entry.getValue())));
    }

    /** Descripción: Valida las condiciones necesarias para «requiredStandaloneDocuments» y rechaza entradas no válidas. Entradas: metamodel: dato de entrada necesario para la operación; editor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> requiredStandaloneDocuments(CapabilitySelection metamodel, EditorSelection editor) {
        List<String> required = new ArrayList<>();
        if(metamodel != null) required.add(metamodel.documentId());
        required.add(editor.editor().documentId());
        if(editor.grammar() != null) required.add(editor.grammar().documentId());
        if(editor.syntaxStyle() != null) required.add(editor.syntaxStyle().documentId());
        return List.copyOf(required);
    }

    /** Descripción: Ejecuta la operación «addTextualRuntimeSources» correspondiente a la responsabilidad de EditorSelection. Entradas: result: dato de entrada necesario para la operación; snapshot: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void addTextualRuntimeSources(Map<String, byte[]> result, GrammarCompilationSnapshot snapshot) {
        snapshot.generatedProject().files().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> put(result, "textual-runtime/source/" + safePath(entry.getKey()), text(entry.getValue())));
        snapshot.runtimeFiles().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> put(result, "frontend/textual-runtime/" + safePath(entry.getKey()), text(entry.getValue())));
        put(result, "textual-runtime/snapshot.json", json(snapshot));
    }

    /** Descripción: Busca una capacidad opcional sin imponer su existencia. Entradas: value y kind. Salida: la selección encontrada o null. */ private CapabilitySelection optionalSelection(HydraCapabilityPackage value, HydraCapabilityKind kind) {
        return value.manifest().capabilities().stream().filter(item -> item.kind() == kind).findFirst().orElse(null);
    }

    /** Descripción: Ejecuta la operación «selection» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: value: valor de entrada que debe procesarse; kind: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private CapabilitySelection selection(HydraCapabilityPackage value, HydraCapabilityKind kind) {
        return value.manifest().capabilities().stream().filter(item -> item.kind() == kind)
                .findFirst().orElseThrow(() -> error(
                        StandaloneEditorGenerationException.Reason.INVALID_REQUEST,
                        "Standalone editor requires capability: " + kind));
    }

    /** Descripción: Selecciona de forma explícita el editor visual o textual solicitado y valida sus dependencias. Entradas: value: paquete de capacidades disponible; editorId: identificador estable del editor solicitado. Salida: devuelve la selección coherente del editor y sus artefactos asociados o rechaza una referencia incompleta. */ private EditorSelection editorSelection(HydraCapabilityPackage value, String editorId) {
        CapabilitySelection textual = matchingSelection(value, HydraCapabilityKind.TEXTUAL_EDITOR_DEFINITION, editorId);
        if(textual != null) {
            String grammarId = textualDefinitionField(value, textual, "grammarId");
            CapabilitySelection grammar = matchingSelection(value, HydraCapabilityKind.GRAMMAR, grammarId);
            if(grammar == null) throw error(StandaloneEditorGenerationException.Reason.INVALID_REQUEST,
                    "Textual standalone editor requires the referenced GRAMMAR capability: " + grammarId);
            String styleId = textualDefinitionField(value, textual, "syntaxStyleDefinitionId");
            CapabilitySelection style = styleId.isBlank() ? null : matchingSelection(value, HydraCapabilityKind.TEXTUAL_SYNTAX_STYLE, styleId);
            if(!styleId.isBlank() && style == null) throw error(StandaloneEditorGenerationException.Reason.INVALID_REQUEST,
                    "Textual standalone editor references a missing syntax style: " + styleId);
            return new EditorSelection(textual, true, grammar, style);
        }
        for(HydraCapabilityKind kind : List.of(HydraCapabilityKind.VISUAL_REPRESENTATION, HydraCapabilityKind.PALETTE, HydraCapabilityKind.FORMS)) {
            CapabilitySelection selected = matchingSelection(value, kind, editorId);
            if(selected != null) return new EditorSelection(selected, false, null, null);
        }
        throw error(StandaloneEditorGenerationException.Reason.INVALID_REQUEST,
                "Standalone editor requires a matching visual or textual editor definition: " + editorId);
    }

    /** Descripción: Ejecuta la operación «matchingSelection» correspondiente a la responsabilidad de EditorSelection. Entradas: value: valor de entrada que debe procesarse; kind: dato de entrada necesario para la operación; requestedId: identificador que delimita el recurso objetivo. Salida: devuelve el resultado definido por el contrato del método. */ private CapabilitySelection matchingSelection(HydraCapabilityPackage value, HydraCapabilityKind kind, String requestedId) {
        List<CapabilitySelection> candidates = value.manifest().capabilities().stream().filter(item -> item.kind() == kind).toList();
        for(CapabilitySelection candidate : candidates) {
            if(candidate.documentId().equals(requestedId)) return candidate;
            JsonNode document = value.canonicalDocuments().get(candidate.documentId());
            if(document != null && requestedId.equals(document.path("id").asText(""))) return candidate;
        }
        return candidates.size() == 1 && requestedId.isBlank() ? candidates.get(0) : null;
    }

    /** Descripción: Ejecuta la operación «textualDefinitionField» correspondiente a la responsabilidad de EditorSelection. Entradas: value: valor de entrada que debe procesarse; selection: dato de entrada necesario para la operación; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String textualDefinitionField(HydraCapabilityPackage value, CapabilitySelection selection, String field) {
        JsonNode document = value.canonicalDocuments().get(selection.documentId());
        return document == null ? "" : document.path(field).asText("").trim();
    }

    /** Descripción: Valida las condiciones necesarias para «validateTextualSnapshot» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; editor: dato de entrada necesario para la operación; snapshot: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateTextualSnapshot(HydraCapabilityPackage value, EditorSelection editor, GrammarCompilationSnapshot snapshot) {
        if(!editor.textual()) return;
        String expectedSnapshot = textualDefinitionField(value, editor.editor(), "compilationSnapshotId");
        String expectedGrammar = textualDefinitionField(value, editor.editor(), "grammarId");
        if(snapshot == null) return;
        if(!snapshot.successful()) throw error(StandaloneEditorGenerationException.Reason.INVALID_REQUEST, "Textual standalone requires a successful grammar snapshot");
        if(!expectedSnapshot.equals(snapshot.id())) throw error(StandaloneEditorGenerationException.Reason.INVALID_REQUEST, "Textual editor is pinned to another grammar snapshot");
        if(!expectedGrammar.equals(snapshot.grammarId())) throw error(StandaloneEditorGenerationException.Reason.INVALID_REQUEST, "Textual editor and grammar snapshot reference different grammars");
    }

    /** Descripción: Ejecuta la operación «textualRuntimeAvailability» correspondiente a la responsabilidad de EditorSelection. Entradas: snapshot: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String textualRuntimeAvailability(GrammarCompilationSnapshot snapshot) {
        if(snapshot == null) return "BUILD_REQUIRED";
        String entry = normalizedRuntimeEntry(snapshot.runtimeDescriptor().browserWorkerEntry());
        if(!entry.isBlank() && snapshot.runtimeFiles().containsKey(entry)) return "BUNDLED_BROWSER_RUNTIME";
        if(!snapshot.runtimeDescriptor().languageServerCommand().isBlank()) return "LANGUAGE_SERVER_HOST_REQUIRED";
        return "BUILD_REQUIRED";
    }

    /** Descripción: Ejecuta la operación «bundledRuntimePath» correspondiente a la responsabilidad de EditorSelection. Entradas: snapshot: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String bundledRuntimePath(GrammarCompilationSnapshot snapshot) {
        if(snapshot == null) return "";
        String entry = normalizedRuntimeEntry(snapshot.runtimeDescriptor().browserWorkerEntry());
        return !entry.isBlank() && snapshot.runtimeFiles().containsKey(entry) ? "frontend/textual-runtime/" + entry : "";
    }

    /** Descripción: Ejecuta la operación «normalizedRuntimeEntry» correspondiente a la responsabilidad de EditorSelection. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String normalizedRuntimeEntry(String value) {
        String normalized = value == null ? "" : value.trim().replace('\\', '/');
        while(normalized.startsWith("./")) normalized = normalized.substring(2);
        return normalized.isBlank() ? "" : safePath(normalized);
    }

    private record EditorSelection(CapabilitySelection editor, boolean textual, CapabilitySelection grammar, CapabilitySelection syntaxStyle) { }

    /** Descripción: Valida las condiciones necesarias para «requireDocument» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; selection: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void requireDocument(HydraCapabilityPackage value, CapabilitySelection selection) {
        if(!value.canonicalDocuments().containsKey(selection.documentId())) throw error(
                StandaloneEditorGenerationException.Reason.INVALID_REQUEST,
                "Required package document is missing: " + selection.documentId());
    }

    /** Descripción: Ejecuta la operación «zip» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: entries: colección de elementos que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] zip(Map<String, byte[]> entries) {
        try(ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                ZipOutputStream output = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for(Map.Entry<String, byte[]> entry : entries.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                ZipEntry item = new ZipEntry(entry.getKey());
                item.setTime(0L);
                output.putNextEntry(item);
                output.write(entry.getValue());
                output.closeEntry();
            }
            output.finish();
            return buffer.toByteArray();
        } catch(IOException exception) {
            throw new StandaloneEditorGenerationException(
                    StandaloneEditorGenerationException.Reason.GENERATION_FAILED,
                    "Could not generate the standalone editor archive", exception);
        }
    }

    /** Descripción: Ejecuta la operación «template» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: relativePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String template(String relativePath) {
        try(InputStream input = new ClassPathResource(TEMPLATE_ROOT + relativePath).getInputStream()) {
            return new String(com.example.editor.io.BoundedIo.read(input, 4L * 1024L * 1024L), StandardCharsets.UTF_8);
        } catch(IOException exception) {
            throw new StandaloneEditorGenerationException(
                    StandaloneEditorGenerationException.Reason.GENERATION_FAILED,
                    "Standalone template is missing: " + relativePath, exception);
        }
    }

    /** Descripción: Ejecuta la operación «templateBytes» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: relativePath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] templateBytes(String relativePath) {
        return template(relativePath).getBytes(StandardCharsets.UTF_8);
    }

    /** Descripción: Ejecuta la operación «json» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] json(Object value) {
        try { return writer.writeValueAsBytes(value); }
        catch(Exception exception) {
            throw new StandaloneEditorGenerationException(
                    StandaloneEditorGenerationException.Reason.GENERATION_FAILED,
                    "Could not serialize generated editor content", exception);
        }
    }

    /** Decodifica recursos binarios embebidos como data URL y conserva texto portable sin cambios. */
    /** Descripción: Ejecuta la operación «resourceBytes» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] resourceBytes(String value) {
        String source = value == null ? "" : value;
        if(source.startsWith("data:")) {
            int separator = source.indexOf(',');
            if(separator > 5 && source.substring(5, separator).toLowerCase(java.util.Locale.ROOT).contains(";base64")) {
                try { return Base64.getDecoder().decode(source.substring(separator + 1)); }
                catch(IllegalArgumentException exception) { throw error(StandaloneEditorGenerationException.Reason.INVALID_REQUEST, "Portable visual asset contains invalid base64 data"); }
            }
        }
        return text(source);
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] text(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    /** Descripción: Ejecuta la operación «put» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: entries: colección de elementos que debe procesarse; path: ruta o ubicación utilizada por la operación; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void put(Map<String, byte[]> entries, String path, byte[] value) {
        String safe = safePath(path);
        if(entries.putIfAbsent(safe, value) != null) throw error(
                StandaloneEditorGenerationException.Reason.GENERATION_FAILED,
                "Duplicate generated file: " + safe);
    }

    /** Descripción: Evalúa la condición «canonicalPath» sin modificar el estado externo. Entradas: documentId: identificador que delimita el recurso objetivo. Salida: devuelve un indicador que expresa el resultado de la condición. */ private String canonicalPath(String documentId) {
        String safe = safePath(documentId);
        return "config/canonical/" + (safe.toLowerCase().endsWith(".json") ? safe : safe + ".json");
    }

    /** Descripción: Ejecuta la operación «safePath» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String safePath(String value) {
        String normalized = value == null ? "" : value.replace('\\', '/').trim();
        if(normalized.isBlank() || normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*"))
            throw error(StandaloneEditorGenerationException.Reason.INVALID_REQUEST,
                    "Generated path is unsafe: " + value);
        List<String> parts = new ArrayList<>();
        for(String part : normalized.split("/")) {
            if(part.isBlank() || ".".equals(part) || "..".equals(part)) throw error(
                    StandaloneEditorGenerationException.Reason.INVALID_REQUEST,
                    "Generated path is unsafe: " + value);
            parts.add(part.replaceAll("[^A-Za-z0-9._:-]", "-"));
        }
        return String.join("/", parts);
    }

    /** Descripción: Ejecuta la operación «identifier» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String identifier(String value) {
        String result = required(value, "Application id")
                .replaceAll("[^A-Za-z0-9._-]", "-").replaceAll("-+", "-");
        if(result.equals(".") || result.equals("..") || result.isBlank()) throw error(
                StandaloneEditorGenerationException.Reason.INVALID_REQUEST,
                "Application id is invalid");
        return result;
    }

    /** Descripción: Valida las condiciones necesarias para «required» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String required(String value, String field) {
        if(value == null || value.isBlank()) throw error(
                StandaloneEditorGenerationException.Reason.INVALID_REQUEST,
                field + " must not be blank");
        return value.trim();
    }

    /** Descripción: Ejecuta la operación «sha256» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch(Exception exception) { throw new IllegalStateException(exception); }
    }

    /** Descripción: Obtiene la información correspondiente a «readme» dentro de StandaloneEditorGeneratorService. Entradas: descriptor: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String readme(StandaloneEditorDescriptor descriptor) {
        String base = """
                # %s

                Editor web independiente generado por HYDRA para ámbito semántico `%s` / editor `%s`.

                ## Requisitos

                - JDK 21 o posterior.
                - Navegador web moderno.

                ## Ejecución

                En Windows ejecute `run.cmd`. En Linux/macOS ejecute `chmod +x build.sh run.sh && ./run.sh`.
                Después abra `http://127.0.0.1:%d`.

                El editor incluye un servidor Java sin dependencias externas y persistencia local atómica con ETag.
                Para evitar perder los modelos al actualizar la aplicación, se recomienda ejecutar directamente:
                `java -cp backend/out HydraStandaloneServer --root=. --port=%d --data-root=/ruta/persistente/modelos`
                (en Windows, indique una ruta persistente bajo AppData del usuario).
                Los guardados nuevos requieren `If-None-Match: *`; las modificaciones requieren `If-Match`
                con el ETag del modelo cargado. No se aceptan escrituras que oculten conflictos.
                No contiene las pantallas de diseño de HYDRA.
                """.formatted(descriptor.name(), descriptor.semanticScopeId(), descriptor.editorId(), descriptor.port(), descriptor.port());
        if(!isTextual(descriptor)) return base + """

                ## Runtime visual

                La distribución incluye las capas portables seleccionadas del paquete `.hydra-cap`. El runtime externo `%s`
                permanece disponible como extensión opcional para integraciones que no puedan ejecutarse de forma portable.
                """.formatted(descriptor.runtimeBaseUrl());
        String availability = descriptor.settings().getOrDefault("textualRuntimeAvailability", "BUILD_REQUIRED");
        String snapshotId = descriptor.settings().getOrDefault("compilationSnapshotId", "");
        String worker = descriptor.settings().getOrDefault("browserWorkerEntry", "");
        return base + """

                ## Runtime textual

                La definición textual está fijada al snapshot de gramática `%s`. Estado de runtime: `%s`.
                El código fuente de la gramática y del runtime generado se conserva para reproducibilidad.

                - `BUNDLED_BROWSER_RUNTIME`: el worker browser materializado y hasheado está incluido%s.
                - `LANGUAGE_SERVER_HOST_REQUIRED`: el snapshot es válido, pero el host LSP/JVM/Node debe proporcionarse fuera del navegador.
                - `BUILD_REQUIRED`: solo se distribuyen fuentes; el borrador puede persistirse, pero no se presenta como modelo semántico validado.

                HYDRA nunca promueve un borrador textual a modelo semántico únicamente por haber podido guardarlo.
                """.formatted(snapshotId, availability, worker.isBlank() ? "" : " en `" + worker + "`");
    }

    /** Descripción: Evalúa la condición «isTextual» sin modificar el estado externo. Entradas: descriptor: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isTextual(StandaloneEditorDescriptor descriptor) {
        return "TEXTUAL".equals(descriptor.settings().getOrDefault("editorKind", "VISUAL"));
    }

    /** Descripción: Ejecuta la operación «jsonString» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String jsonString(String value) {
        try { return writer.writeValueAsString(value).replaceFirst("^\"", "").replaceFirst("\"$", ""); }
        catch(Exception exception) { return value.replace("\\", "\\\\").replace("\"", "\\\""); }
    }

    /** Descripción: Ejecuta la operación «escapeHtml» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Descripción: Ejecuta la operación «error» correspondiente a la responsabilidad de StandaloneEditorGeneratorService. Entradas: reason: dato de entrada necesario para la operación; message: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private StandaloneEditorGenerationException error(
            StandaloneEditorGenerationException.Reason reason, String message) {
        return new StandaloneEditorGenerationException(reason, message);
    }
}
