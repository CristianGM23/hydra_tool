package com.example.editor.metamodel.importing.python;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import com.example.editor.concurrent.BoundedPermitPool;
import com.example.editor.security.process.HydraExternalProcessPolicy;
import com.example.editor.security.process.HydraExternalProcessSecurityProperties;
import com.example.editor.security.process.HydraProcessTreeTerminator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.example.editor.metamodel.design.MetamodelDesignException;
import com.example.editor.metamodel.design.MetamodelDesignException.Reason;
import com.example.editor.metamodel.importing.ImportSourceEntry;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Usa ast.parse en un proceso aislado y recurre a un parser conservador si Python no está disponible. */
@Component
public class PythonProcessAstAnalyzer implements PythonAstAnalyzer {

    private static final String SCRIPT = """
            import ast, json, sys
            path = sys.argv[1]
            module = sys.argv[2]
            source = sys.stdin.read()
            tree = ast.parse(source, filename=path, type_comments=True)

            def text(node):
                if node is None:
                    return ''
                try:
                    return ast.unparse(node)
                except Exception:
                    return ''

            def infer(node):
                if isinstance(node, ast.Constant):
                    if isinstance(node.value, bool): return 'bool'
                    if isinstance(node.value, int): return 'int'
                    if isinstance(node.value, float): return 'float'
                    if isinstance(node.value, str): return 'str'
                    if isinstance(node.value, bytes): return 'bytes'
                    if node.value is None: return 'Optional[Any]'
                if isinstance(node, (ast.List, ast.ListComp)): return 'list[Any]'
                if isinstance(node, (ast.Set, ast.SetComp)): return 'set[Any]'
                if isinstance(node, (ast.Dict, ast.DictComp)): return 'dict[str, Any]'
                return 'Any'

            def parameter_specs(args):
                result = []
                positional = list(args.posonlyargs) + list(args.args)
                defaults = [None] * (len(positional) - len(args.defaults)) + list(args.defaults)
                for arg, default in zip(positional, defaults):
                    if arg.arg in ('self', 'cls'): continue
                    result.append({'name': arg.arg, 'type': text(arg.annotation) or 'Any', 'defaultValue': text(default)})
                if args.vararg:
                    result.append({'name': args.vararg.arg, 'type': 'list[' + (text(args.vararg.annotation) or 'Any') + ']', 'defaultValue': ''})
                for arg, default in zip(args.kwonlyargs, args.kw_defaults):
                    result.append({'name': arg.arg, 'type': text(arg.annotation) or 'Any', 'defaultValue': text(default)})
                if args.kwarg:
                    result.append({'name': args.kwarg.arg, 'type': 'dict[str, ' + (text(args.kwarg.annotation) or 'Any') + ']', 'defaultValue': ''})
                return result

            result = []
            def collect_class(node, owner=''):
                qualified = (owner + '.' if owner else (module + '.' if module else '')) + node.name
                decorators = [text(item) for item in node.decorator_list]
                bases = [text(item) for item in node.bases]
                is_enum = any(base.split('.')[-1] in ('Enum', 'IntEnum', 'StrEnum', 'Flag', 'IntFlag') for base in bases)
                fields = {}
                methods = []
                enum_literals = []
                for item in node.body:
                    if isinstance(item, ast.AnnAssign) and isinstance(item.target, ast.Name):
                        fields[item.target.id] = {'name': item.target.id, 'type': text(item.annotation) or infer(item.value), 'defaultValue': text(item.value), 'decorators': [], 'classVariable': True}
                    elif isinstance(item, ast.Assign):
                        for target in item.targets:
                            if isinstance(target, ast.Name):
                                if is_enum and not target.id.startswith('_'):
                                    enum_literals.append(target.id)
                                else:
                                    fields.setdefault(target.id, {'name': target.id, 'type': infer(item.value), 'defaultValue': text(item.value), 'decorators': [], 'classVariable': True})
                    elif isinstance(item, (ast.FunctionDef, ast.AsyncFunctionDef)):
                        method_decorators = [text(value) for value in item.decorator_list]
                        methods.append({'name': item.name, 'returnType': text(item.returns) or 'Any', 'parameters': parameter_specs(item.args), 'decorators': method_decorators, 'asynchronous': isinstance(item, ast.AsyncFunctionDef), 'hasBody': bool(item.body)})
                        if item.name == '__init__':
                            for nested in ast.walk(item):
                                if isinstance(nested, ast.AnnAssign) and isinstance(nested.target, ast.Attribute) and isinstance(nested.target.value, ast.Name) and nested.target.value.id == 'self':
                                    fields.setdefault(nested.target.attr, {'name': nested.target.attr, 'type': text(nested.annotation) or infer(nested.value), 'defaultValue': text(nested.value), 'decorators': [], 'classVariable': False})
                                elif isinstance(nested, ast.Assign):
                                    for target in nested.targets:
                                        if isinstance(target, ast.Attribute) and isinstance(target.value, ast.Name) and target.value.id == 'self':
                                            fields.setdefault(target.attr, {'name': target.attr, 'type': infer(nested.value), 'defaultValue': text(nested.value), 'decorators': [], 'classVariable': False})
                        if any(value.split('.')[-1] == 'property' for value in method_decorators):
                            fields.setdefault(item.name, {'name': item.name, 'type': text(item.returns) or 'Any', 'defaultValue': '', 'decorators': method_decorators, 'classVariable': False})
                    elif isinstance(item, ast.ClassDef):
                        collect_class(item, qualified)
                result.append({'name': node.name, 'qualifiedName': qualified, 'module': module, 'sourcePath': path, 'bases': bases, 'decorators': decorators, 'fields': list(fields.values()), 'methods': methods, 'enumLiterals': enum_literals})

            for item in tree.body:
                if isinstance(item, ast.ClassDef):
                    collect_class(item)
            print(json.dumps({'types': result}, ensure_ascii=False))
            """;

    /**
     * Runs the constant HYDRA AST helper without placing multiline text in the command line.
     * User Python source still travels exclusively through stdin.
     */
    private static final String SCRIPT_ARGUMENT = Base64.getEncoder().encodeToString(SCRIPT.getBytes(StandardCharsets.UTF_8));
    private static final String SCRIPT_BOOTSTRAP =
            "import base64,sys;code=base64.b64decode(sys.argv[1]);sys.argv=[sys.argv[0]]+sys.argv[2:];exec(compile(code,'<hydra-python-ast>','exec'))";

    private final ObjectMapper objectMapper;
    private final PythonAstImportProperties properties;
    private final BoundedPermitPool processSlots;
    private final Executor blockingIoExecutor;
    private final HydraExternalProcessPolicy securityPolicy;
    private final HydraProcessTreeTerminator processTreeTerminator;

    @Autowired
    /** Descripción: Inicializa una instancia de PythonProcessAstAnalyzer con sus dependencias y datos de entrada. Entradas: objectMapper: componente de conversión utilizado durante el procesamiento; properties: configuración que gobierna el comportamiento; blockingIoExecutor: dato de entrada necesario para la operación; securityPolicy: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ public PythonProcessAstAnalyzer(ObjectMapper objectMapper, PythonAstImportProperties properties,
            @Qualifier("hydraBlockingIoExecutor") Executor blockingIoExecutor, HydraExternalProcessPolicy securityPolicy) {
        this(objectMapper, properties, blockingIoExecutor, securityPolicy, new HydraProcessTreeTerminator());
    }

    /** Descripción: Inicializa una instancia de PythonProcessAstAnalyzer con sus dependencias y datos de entrada. Entradas: objectMapper: componente de conversión utilizado durante el procesamiento; properties: configuración que gobierna el comportamiento; blockingIoExecutor: dato de entrada necesario para la operación; securityPolicy: dato de entrada necesario para la operación; processTreeTerminator: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ PythonProcessAstAnalyzer(ObjectMapper objectMapper, PythonAstImportProperties properties, Executor blockingIoExecutor,
            HydraExternalProcessPolicy securityPolicy, HydraProcessTreeTerminator processTreeTerminator) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.blockingIoExecutor = blockingIoExecutor;
        this.securityPolicy = securityPolicy;
        this.processTreeTerminator = processTreeTerminator;
        this.processSlots = new BoundedPermitPool(properties.getMaximumConcurrentProcesses());
    }

    /** Compatibility constructor for focused unit tests and embedders. */
    /** Descripción: Inicializa una instancia de PythonProcessAstAnalyzer con sus dependencias y datos de entrada. Entradas: objectMapper: componente de conversión utilizado durante el procesamiento; properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public PythonProcessAstAnalyzer(ObjectMapper objectMapper, PythonAstImportProperties properties) {
        this(objectMapper, properties, ForkJoinPool.commonPool(), compatibilitySecurityPolicy(properties));
    }

    /**
     * Keeps the focused-test/embedder constructor secure while honoring the executable explicitly configured
     * for this analyzer. The production Spring constructor still receives the centrally governed policy.
     */
    /** Descripción: Ejecuta la operación «compatibilitySecurityPolicy» correspondiente a la responsabilidad de PythonProcessAstAnalyzer. Entradas: properties: configuración que gobierna el comportamiento. Salida: devuelve el resultado definido por el contrato del método. */ private static HydraExternalProcessPolicy compatibilitySecurityPolicy(PythonAstImportProperties properties) {
        HydraExternalProcessSecurityProperties security = new HydraExternalProcessSecurityProperties();
        java.util.LinkedHashSet<String> allowed = new java.util.LinkedHashSet<>(security.getAllowedExecutables());
        if(properties != null && properties.getExecutable() != null && !properties.getExecutable().isBlank()) {
            allowed.add(properties.getExecutable());
        }
        security.setAllowedExecutables(allowed);
        return new HydraExternalProcessPolicy(security);
    }

    @Override
    /** Descripción: Ejecuta la operación «analyze» correspondiente a la responsabilidad de PythonProcessAstAnalyzer. Entradas: sources: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public PythonProjectAnalysis analyze(List<ImportSourceEntry> sources) {
        List<PythonTypeSpec> types = new ArrayList<>();
        List<String> modules = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        boolean fallbackUsed = false;
        for(ImportSourceEntry source : sources) {
            String module = moduleName(source.path());
            modules.add(module);
            try {
                types.addAll(runAst(source, module));
            } catch(PythonAstCancellationException exception) {
                throw new MetamodelDesignException(Reason.INVALID_REQUEST, exception.getMessage(), exception);
            } catch(RecoverablePythonAstException exception) {
                if(!properties.isFallbackEnabled()) throw new MetamodelDesignException(Reason.INVALID_REQUEST, exception.getMessage(), exception);
                fallbackUsed = true;
                diagnostics.add(exception.getMessage() + " Conservative fallback was used for " + source.path() + ".");
                types.addAll(fallback(source, module));
            }
        }
        return new PythonProjectAnalysis(types, modules.stream().distinct().toList(), fallbackUsed, diagnostics);
    }

    /** Descripción: Ejecuta la operación «availableProcessSlots» correspondiente a la responsabilidad de PythonProcessAstAnalyzer. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int availableProcessSlots(){return processSlots.availablePermits();}
    /** Descripción: Ejecuta la operación «capacityStatus» correspondiente a la responsabilidad de PythonProcessAstAnalyzer. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public BoundedPermitPool.Snapshot capacityStatus(){return processSlots.snapshot();}

    /** Descripción: Procesa la operación «runAst» coordinando las responsabilidades de PythonProcessAstAnalyzer. Entradas: source: dato de entrada necesario para la operación; module: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<PythonTypeSpec> runAst(ImportSourceEntry source, String module) {
        Process process;
        HydraProcessTreeTerminator.ProcessTreeTracker tracker=null;
        BoundedPermitPool.Permit permit=null;
        try {
            permit=processSlots.tryAcquire(properties.getProcessAcquireTimeoutSeconds(), TimeUnit.SECONDS);
            if(permit == null) throw new RecoverablePythonAstException("Python AST analysis capacity is busy", null);
            List<String> command=List.of(properties.getExecutable(), "-I", "-S", "-c", SCRIPT_BOOTSTRAP, SCRIPT_ARGUMENT, source.path(), module);
            securityPolicy.validateCommand(command);
            ProcessBuilder builder = new ProcessBuilder(command);
            securityPolicy.configureMinimalEnvironment(builder, java.util.Map.of("PYTHONUTF8", "1", "PYTHONIOENCODING", "UTF-8"));
            process = builder.start();
            tracker = processTreeTerminator.track(process,
                    Duration.ofMillis(properties.getProcessTreeObservationIntervalMillis()));
        } catch(IllegalArgumentException exception) {
            if(permit != null) permit.close();
            throw new RecoverablePythonAstException("Python AST process was rejected by the external-process security policy", exception);
        } catch(InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PythonAstCancellationException("Python AST analysis was cancelled while waiting for capacity", exception);
        } catch(IOException exception) {
            if(permit != null) permit.close();
            throw new RecoverablePythonAstException("Python AST executable could not be started", exception);
        }

        CompletableFuture<byte[]> stdout = CompletableFuture.supplyAsync(() -> readLimited(process.getInputStream()), blockingIoExecutor);
        CompletableFuture<byte[]> stderr = CompletableFuture.supplyAsync(() -> readLimited(process.getErrorStream()), blockingIoExecutor);
        try {
            process.getOutputStream().write(source.content());
            process.getOutputStream().close();
            if(!process.waitFor(properties.getTimeoutSeconds(), TimeUnit.SECONDS)) {
                terminate(tracker, process);
                tracker = null;
                throw new RecoverablePythonAstException("Python AST analysis timed out", null);
            }
            terminate(tracker, process);
            tracker = null;
            byte[] output;
            String error;
            try {
                long joinTimeout=Math.max(100L,properties.getOutputCollectionTimeoutMillis());
                output = stdout.get(joinTimeout, TimeUnit.MILLISECONDS);
                error = new String(stderr.get(joinTimeout, TimeUnit.MILLISECONDS), StandardCharsets.UTF_8).trim();
            } catch(TimeoutException exception) {
                stdout.cancel(true);
                stderr.cancel(true);
                terminate(tracker, process);
                tracker = null;
                throw new RecoverablePythonAstException("Python AST output collection timed out", exception);
            } catch(ExecutionException exception) {
                terminate(tracker, process);
                tracker = null;
                Throwable cause = exception.getCause();
                if(cause instanceof RecoverablePythonAstException recoverable) throw recoverable;
                throw new RecoverablePythonAstException("Python AST output could not be collected", cause == null ? exception : cause);
            }
            if(process.exitValue() != 0) throw new MetamodelDesignException(Reason.INVALID_REQUEST, "Python AST parsing failed for " + source.path() + ": " + error);
            return parse(output);
        } catch(IOException exception) {
            terminate(tracker, process);
            tracker = null;
            throw new RecoverablePythonAstException("Python AST process communication failed", exception);
        } catch(InterruptedException exception) {
            stdout.cancel(true);
            stderr.cancel(true);
            terminate(tracker, process);
            tracker = null;
            Thread.currentThread().interrupt();
            throw new PythonAstCancellationException("Python AST analysis was cancelled", exception);
        } finally {
            if(tracker != null) terminate(tracker, process);
            if(!stdout.isDone()) stdout.cancel(true);
            if(!stderr.isDone()) stderr.cancel(true);
            if(permit != null) permit.close();
        }
    }

    /** Descripción: Ejecuta la operación «terminate» correspondiente a la responsabilidad de PythonAstCancellationException. Entradas: tracker: dato de entrada necesario para la operación; process: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private HydraProcessTreeTerminator.TerminationReport terminate(HydraProcessTreeTerminator.ProcessTreeTracker tracker, Process process) {
        if(tracker != null) {
            return processTreeTerminator.terminateTracked(tracker,
                    Duration.ofMillis(properties.getProcessTerminationGraceMillis()),
                    Duration.ofMillis(properties.getProcessTerminationForceWaitMillis()),
                    properties.getProcessTerminationMaximumPasses());
        }
        return processTreeTerminator.terminate(process,
                Duration.ofMillis(properties.getProcessTerminationGraceMillis()),
                Duration.ofMillis(properties.getProcessTerminationForceWaitMillis()),
                properties.getProcessTerminationMaximumPasses());
    }

    /** Descripción: Obtiene la información correspondiente a «readLimited» dentro de PythonProcessAstAnalyzer. Entradas: input: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private byte[] readLimited(InputStream input) {
        try(input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while((read = input.read(buffer)) >= 0) {
                if(read == 0) continue;
                if(output.size() + read > properties.getMaximumOutputBytes()) throw new IOException("Python AST output exceeds the configured limit");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch(IOException exception) {
            throw new RecoverablePythonAstException("Python AST output could not be read", exception);
        }
    }

    /** Descripción: Ejecuta la operación «parse» correspondiente a la responsabilidad de PythonProcessAstAnalyzer. Entradas: content: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<PythonTypeSpec> parse(byte[] content) {
        try {
            JsonNode root = objectMapper.readTree(content);
            List<PythonTypeSpec> result = new ArrayList<>();
            JsonNode types = root.path("types");
            if(types.isArray()) for(JsonNode item : types) result.add(type(item));
            return List.copyOf(result);
        } catch(JacksonException exception) {
            throw new RecoverablePythonAstException("Python AST output was not valid JSON", exception);
        }
    }

    /** Descripción: Ejecuta la operación «type» correspondiente a la responsabilidad de PythonProcessAstAnalyzer. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private PythonTypeSpec type(JsonNode node) {
        List<PythonFieldSpec> fields = new ArrayList<>();
        for(JsonNode field : node.path("fields")) fields.add(new PythonFieldSpec(text(field, "name"), text(field, "type"), text(field, "defaultValue"), strings(field.path("decorators")), field.path("classVariable").asBoolean(false)));
        List<PythonMethodSpec> methods = new ArrayList<>();
        for(JsonNode method : node.path("methods")) {
            List<PythonParameterSpec> parameters = new ArrayList<>();
            for(JsonNode parameter : method.path("parameters")) parameters.add(new PythonParameterSpec(text(parameter, "name"), text(parameter, "type"), text(parameter, "defaultValue")));
            methods.add(new PythonMethodSpec(text(method, "name"), text(method, "returnType"), parameters, strings(method.path("decorators")), method.path("asynchronous").asBoolean(false), method.path("hasBody").asBoolean(false)));
        }
        return new PythonTypeSpec(text(node, "name"), text(node, "qualifiedName"), text(node, "module"), text(node, "sourcePath"), strings(node.path("bases")), strings(node.path("decorators")), fields, methods, strings(node.path("enumLiterals")));
    }

    /** Descripción: Ejecuta la operación «strings» correspondiente a la responsabilidad de PythonProcessAstAnalyzer. Entradas: node: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> strings(JsonNode node) {
        List<String> result = new ArrayList<>();
        if(node.isArray()) for(JsonNode item : node) result.add(item.asText(""));
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «text» correspondiente a la responsabilidad de PythonProcessAstAnalyzer. Entradas: node: dato de entrada necesario para la operación; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String text(JsonNode node, String field) {
        return node.path(field).asText("");
    }

    /** Descripción: Ejecuta la operación «moduleName» correspondiente a la responsabilidad de PythonProcessAstAnalyzer. Entradas: path: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String moduleName(String path) {
        String value = path.replace('\\', '/');
        if(value.toLowerCase(Locale.ROOT).endsWith(".py")) value = value.substring(0, value.length() - 3);
        value = value.replaceAll("(^|/)__init__$", "").replace('/', '.').replaceAll("^\\.+|\\.+$", "");
        return value;
    }

    /** Descripción: Ejecuta la operación «fallback» correspondiente a la responsabilidad de PythonProcessAstAnalyzer. Entradas: source: dato de entrada necesario para la operación; module: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<PythonTypeSpec> fallback(ImportSourceEntry source, String module) {
        String text = new String(source.content(), StandardCharsets.UTF_8);
        Pattern classPattern = Pattern.compile("^(\\s*)class\\s+([A-Za-z_][A-Za-z0-9_]*)(?:\\(([^)]*)\\))?\\s*:", Pattern.MULTILINE);
        Matcher matcher = classPattern.matcher(text);
        List<PythonTypeSpec> result = new ArrayList<>();
        while(matcher.find()) {
            int indent = matcher.group(1).length();
            String name = matcher.group(2);
            int bodyStart = matcher.end();
            int bodyEnd = nextClassOrDedent(text, bodyStart, indent);
            String body = text.substring(bodyStart, bodyEnd);
            List<String> bases = split(matcher.group(3));
            List<PythonFieldSpec> fields = fallbackFields(body);
            List<PythonMethodSpec> methods = fallbackMethods(body);
            List<String> enumLiterals = bases.stream().anyMatch(value -> value.endsWith("Enum")) ? fallbackEnumLiterals(body) : List.of();
            result.add(new PythonTypeSpec(name, module.isBlank() ? name : module + "." + name, module, source.path(), bases, List.of(), fields, methods, enumLiterals));
        }
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «nextClassOrDedent» correspondiente a la responsabilidad de PythonProcessAstAnalyzer. Entradas: text: valor de entrada que debe procesarse; start: dato de entrada necesario para la operación; indent: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private int nextClassOrDedent(String text, int start, int indent) {
        Pattern line = Pattern.compile("(?m)^(\\s*)(?:class|def|@|[A-Za-z_])");
        Matcher matcher = line.matcher(text);
        matcher.region(start, text.length());
        while(matcher.find()) if(matcher.group(1).length() <= indent && matcher.start() > start) return matcher.start();
        return text.length();
    }

    /** Descripción: Ejecuta la operación «fallbackFields» correspondiente a la responsabilidad de PythonProcessAstAnalyzer. Entradas: body: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<PythonFieldSpec> fallbackFields(String body) {
        Pattern pattern = Pattern.compile("(?m)^\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*([^=\\n]+)(?:=\\s*([^\\n#]+))?");
        Matcher matcher = pattern.matcher(body);
        List<PythonFieldSpec> result = new ArrayList<>();
        while(matcher.find()) result.add(new PythonFieldSpec(matcher.group(1), matcher.group(2).trim(), matcher.group(3) == null ? "" : matcher.group(3).trim(), List.of(), true));
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «fallbackMethods» correspondiente a la responsabilidad de PythonProcessAstAnalyzer. Entradas: body: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<PythonMethodSpec> fallbackMethods(String body) {
        Pattern pattern = Pattern.compile("(?m)^\\s+(?:async\\s+)?def\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)\\s*(?:->\\s*([^:]+))?:");
        Matcher matcher = pattern.matcher(body);
        List<PythonMethodSpec> result = new ArrayList<>();
        while(matcher.find()) {
            List<PythonParameterSpec> parameters = new ArrayList<>();
            for(String value : split(matcher.group(2))) {
                String[] pieces = value.trim().split("[:=]", 3);
                if(pieces.length > 0 && !pieces[0].equals("self") && !pieces[0].equals("cls")) parameters.add(new PythonParameterSpec(pieces[0].trim(), pieces.length > 1 ? pieces[1].trim() : "Any", pieces.length > 2 ? pieces[2].trim() : ""));
            }
            result.add(new PythonMethodSpec(matcher.group(1), matcher.group(3) == null ? "Any" : matcher.group(3).trim(), parameters, List.of(), matcher.group().stripLeading().startsWith("async"), true));
        }
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «fallbackEnumLiterals» correspondiente a la responsabilidad de PythonProcessAstAnalyzer. Entradas: body: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> fallbackEnumLiterals(String body) {
        Pattern pattern = Pattern.compile("(?m)^\\s+([A-Z][A-Z0-9_]*)\\s*=");
        Matcher matcher = pattern.matcher(body);
        List<String> result = new ArrayList<>();
        while(matcher.find()) result.add(matcher.group(1));
        return List.copyOf(result);
    }

    /** Descripción: Ejecuta la operación «split» correspondiente a la responsabilidad de PythonProcessAstAnalyzer. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private List<String> split(String value) {
        if(value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    private static final class PythonAstCancellationException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        /** Descripción: Inicializa una instancia de PythonAstCancellationException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private PythonAstCancellationException(String message, Throwable cause) { super(message, cause); }
    }

    private static final class RecoverablePythonAstException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        /** Descripción: Inicializa una instancia de RecoverablePythonAstException con sus dependencias y datos de entrada. Entradas: message: dato de entrada necesario para la operación; cause: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ private RecoverablePythonAstException(String message, Throwable cause) { super(message, cause); }
    }
}
