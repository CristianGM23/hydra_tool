package com.example.editor.audit;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Fallback que cubre automáticamente endpoints presentes y futuros. Los módulos
 * pueden registrar un clasificador con mayor prioridad para nombres más ricos.
 */
@Component
public class GenericAuditHttpActivityClassifier implements AuditHttpActivityClassifier {
    private static final Set<String> ACTION_TOKENS = Set.of("create", "update", "delete", "save", "import", "export", "download",
            "validate", "validation", "analyze", "analyse", "execute", "run", "compile", "deploy", "generate", "quickfix",
            "quick-fix", "repair", "apply", "undo", "redo", "rollback", "restore", "open", "close", "settings", "configure");

    @Override
    /** Descripción: Ejecuta la operación «classify» correspondiente a la responsabilidad de GenericAuditHttpActivityClassifier. Entradas: method: dato de entrada necesario para la operación; requestPath: ruta o ubicación utilizada por la operación. Salida: devuelve el resultado definido por el contrato del método. */ public Optional<AuditHttpActivityClassification> classify(String method, String requestPath) {
        if(requestPath == null || !requestPath.startsWith("/api/")) return Optional.empty();
        String normalizedMethod = method == null ? "GET" : method.trim().toUpperCase(Locale.ROOT);
        String path = requestPath.split("\\?", 2)[0].replaceAll("/+", "/");
        List<String> tokens = Arrays.stream(path.substring(5).split("/")).filter(value -> !value.isBlank()).toList();
        if(tokens.isEmpty()) return Optional.empty();
        String moduleId = normalizeModule(tokens.get(0));
        String action = action(normalizedMethod, tokens);
        boolean semantic = isSemanticAction(action, normalizedMethod);
        String resourceId = resourceId(tokens);
        String displayName = displayName(tokens, action);
        String activityType = "api." + identifier(moduleId) + "." + action.toLowerCase(Locale.ROOT);
        return Optional.of(new AuditHttpActivityClassification(activityType, moduleId, action, AuditImportance.INFO,
                "api-operation", resourceId, displayName, semantic));
    }

    /** Descripción: Ejecuta la operación «priority» correspondiente a la responsabilidad de GenericAuditHttpActivityClassifier. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public int priority() { return Integer.MIN_VALUE; }

    /** Descripción: Ejecuta la operación «action» correspondiente a la responsabilidad de GenericAuditHttpActivityClassifier. Entradas: method: dato de entrada necesario para la operación; tokens: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String action(String method, List<String> tokens) {
        String moduleToken = tokens.get(0).toLowerCase(Locale.ROOT);
        String operationPath = tokens.size() <= 1 ? "" : String.join("/", tokens.subList(1, tokens.size())).toLowerCase(Locale.ROOT);
        if(operationPath.contains("undo")) return AuditActions.UNDO;
        if(operationPath.contains("redo")) return AuditActions.REDO;
        if(operationPath.contains("create")) return AuditActions.CREATE;
        if(operationPath.contains("delete") || operationPath.contains("remove")) return AuditActions.DELETE;
        if(operationPath.contains("update") || operationPath.contains("rename")) return AuditActions.UPDATE;
        if(operationPath.contains("save")) return AuditActions.SAVE;
        if(operationPath.contains("rollback") || operationPath.contains("restore")) return AuditActions.ROLLBACK;
        if(operationPath.contains("quick-fix") || operationPath.contains("quickfix") || operationPath.contains("repair") || operationPath.contains("apply")) return AuditActions.APPLY;
        if(operationPath.contains("import")) return AuditActions.IMPORT;
        if(operationPath.contains("export") || operationPath.contains("download") || moduleToken.contains("export")) return AuditActions.EXPORT;
        if(operationPath.contains("validate") || operationPath.contains("analy") || ((moduleToken.startsWith("validat") || moduleToken.contains("metamodel")) && operationPath.contains("run"))) return AuditActions.VALIDATE;
        if(operationPath.contains("execute") || operationPath.contains("run") || operationPath.contains("compile") || operationPath.contains("deploy") || operationPath.contains("generate")) return AuditActions.EXECUTE;
        if(operationPath.contains("settings") || operationPath.contains("configure") || operationPath.contains("configuration")) return AuditActions.CONFIGURE;
        if(operationPath.contains("open") || operationPath.contains("preview") || operationPath.contains("launch")) return AuditActions.OPEN;
        if(operationPath.contains("close")) return AuditActions.CLOSE;
        return switch(method) {
            case "DELETE" -> AuditActions.DELETE;
            case "PUT", "PATCH" -> AuditActions.UPDATE;
            case "POST" -> AuditActions.SAVE;
            default -> AuditActions.OPEN;
        };
    }

    /** Descripción: Evalúa la condición «isSemanticAction» sin modificar el estado externo. Entradas: action: dato de entrada necesario para la operación; method: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isSemanticAction(String action, String method) {
        if("GET".equals(method)) return AuditActions.EXPORT.equals(action) || AuditActions.OPEN.equals(action);
        return Set.of(AuditActions.IMPORT, AuditActions.EXPORT, AuditActions.VALIDATE, AuditActions.EXECUTE, AuditActions.APPLY,
                AuditActions.ROLLBACK, AuditActions.UNDO, AuditActions.REDO, AuditActions.CONFIGURE).contains(action);
    }

    /** Descripción: Ejecuta la operación «normalizeModule» correspondiente a la responsabilidad de GenericAuditHttpActivityClassifier. Entradas: token: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String normalizeModule(String token) {
        String normalized = token.toLowerCase(Locale.ROOT);
        if(normalized.contains("metamodel")) return "metamodel";
        if(normalized.startsWith("validat") || normalized.contains("quick-fix") || normalized.contains("quickfix")) return "validation";
        if(normalized.startsWith("event-flow")) return "event-flow";
        if(normalized.startsWith("event")) return "event";
        if(normalized.startsWith("gamification")) return "gamification";
        if(normalized.startsWith("integration")) return "integration";
        if(normalized.startsWith("editor")) return "editor";
        if(normalized.startsWith("model")) return "model";
        if(normalized.startsWith("project")) return "project";
        if(normalized.startsWith("update-site") || normalized.contains("import")) return "import";
        if(normalized.startsWith("compatibility")) return "compatibility";
        if(normalized.startsWith("distribution")) return "distribution";
        return identifier(normalized);
    }

    /** Descripción: Ejecuta la operación «resourceId» correspondiente a la responsabilidad de GenericAuditHttpActivityClassifier. Entradas: tokens: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String resourceId(List<String> tokens) {
        for(int index = tokens.size() - 1; index >= 0; index--) {
            String token = tokens.get(index);
            if(!ACTION_TOKENS.contains(token.toLowerCase(Locale.ROOT)) && !token.matches("\\d+")) return limit(token, 120);
        }
        return limit(String.join("/", tokens), 120);
    }

    /** Descripción: Ejecuta la operación «displayName» correspondiente a la responsabilidad de GenericAuditHttpActivityClassifier. Entradas: tokens: dato de entrada necesario para la operación; action: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String displayName(List<String> tokens, String action) {
        String candidate = tokens.stream().filter(token -> !ACTION_TOKENS.contains(token.toLowerCase(Locale.ROOT)))
                .reduce((first, second) -> second).orElse(tokens.get(0));
        String human = humanize(candidate);
        return human.isBlank() ? humanize(action) : human;
    }

    /** Descripción: Ejecuta la operación «identifier» correspondiente a la responsabilidad de GenericAuditHttpActivityClassifier. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String identifier(String value) {
        String normalized = value == null ? "general" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isBlank() ? "general" : limit(normalized, 80);
    }
    /** Descripción: Ejecuta la operación «humanize» correspondiente a la responsabilidad de GenericAuditHttpActivityClassifier. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private String humanize(String value) {
        String text = value == null ? "" : value.replace('-', ' ').replace('_', ' ').replace('.', ' ').trim();
        return text.isBlank() ? "Operation" : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
    /** Descripción: Ejecuta la operación «limit» correspondiente a la responsabilidad de GenericAuditHttpActivityClassifier. Entradas: value: valor de entrada que debe procesarse; max: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private String limit(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
