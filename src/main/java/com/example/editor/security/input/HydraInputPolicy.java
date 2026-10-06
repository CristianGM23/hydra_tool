package com.example.editor.security.input;

import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Política transversal de entrada.
 *
 * No "sanitiza" silenciosamente valores porque eso puede cambiar semántica de
 * modelos o DSLs. En su lugar normaliza Unicode cuando se valida texto plano y
 * rechaza entradas fuera de presupuesto, caracteres de control y marcado activo
 * en campos cuyo contrato es descriptivo. Las expresiones/código conservan su
 * contenido y son responsabilidad de sus parsers/runtimes acotados.
 */
@Component
public class HydraInputPolicy {
    private static final Pattern FORBIDDEN_CONTROL = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
    private static final Pattern ACTIVE_MARKUP = Pattern.compile(
            "(?is)(<\\s*(script|iframe|object|embed|svg|math)\\b|javascript\\s*:|data\\s*:\\s*text/html|on[a-z]{3,20}\\s*=|\\$\\{\\s*jndi\\s*:)");
    private static final Pattern PLAIN_FIELD = Pattern.compile(
            "(?i)(name|title|label|description|message|displayName|summary|purpose|recommendation|rationale|help|hint|heading|subtitle|author|license|obligations)");
    private static final Pattern EXECUTABLE_FIELD = Pattern.compile(
            "(?i).*(expression|script|code|source|template|query|body|content|payload|blockly|ocl|evl|eol|atl).*" );

    private final HydraInputSecurityProperties properties;

    /** Descripción: Inicializa una instancia de HydraInputPolicy con sus dependencias y datos de entrada. Entradas: properties: configuración que gobierna el comportamiento. Salida: deja la instancia inicializada y lista para su uso. */ public HydraInputPolicy(HydraInputSecurityProperties properties) {
        this.properties = properties;
    }

    /** Descripción: Ejecuta la operación «plainText» correspondiente a la responsabilidad de HydraInputPolicy. Entradas: field: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse; maxCharacters: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String plainText(String field, String value, int maxCharacters) {
        if(value == null) return null;
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);
        validateStringBudget(field, normalized, Math.min(maxCharacters, properties.getMaxPlainTextCharacters()));
        if(ACTIVE_MARKUP.matcher(normalized).find()) {
            throw new HydraInputValidationException(field, "Potentially executable markup is not allowed in plain-text fields");
        }
        return normalized;
    }

    /** Descripción: Ejecuta la operación «identifier» correspondiente a la responsabilidad de HydraInputPolicy. Entradas: field: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse; maxCharacters: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public String identifier(String field, String value, int maxCharacters) {
        if(value == null) return null;
        validateStringBudget(field, value, maxCharacters);
        if(value.indexOf('/') >= 0 || value.indexOf('\\') >= 0 || value.contains("..")) {
            throw new HydraInputValidationException(field, field + " contains path syntax that is not valid for an identifier");
        }
        return value;
    }

    /** Descripción: Ejecuta la operación «httpsUrl» correspondiente a la responsabilidad de HydraInputPolicy. Entradas: field: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ public URI httpsUrl(String field, String value) {
        validateStringBudget(field, value, 4096);
        try {
            URI uri = new URI(value);
            if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new HydraInputValidationException(field, field + " must be an absolute HTTPS URL");
            }
            if(uri.getUserInfo() != null) {
                throw new HydraInputValidationException(field, field + " must not contain embedded credentials");
            }
            return uri;
        } catch(URISyntaxException exception) {
            throw new HydraInputValidationException(field, field + " is not a valid URL");
        }
    }

    /** Descripción: Valida las condiciones necesarias para «validatePayload» y rechaza entradas no válidas. Entradas: body: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void validatePayload(Object body) {
        if(body == null) return;
        Budget budget = new Budget(properties.getMaxPayloadNodes());
        inspect(body, "$", "", 0, budget, java.util.Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    /** Descripción: Valida las condiciones necesarias para «validateParameter» y rechaza entradas no válidas. Entradas: field: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void validateParameter(String field, String value) {
        validateStringBudget(field, value, properties.getMaxParameterCharacters());
    }

    /** Descripción: Ejecuta la operación «inspect» correspondiente a la responsabilidad de HydraInputPolicy. Entradas: value: valor de entrada que debe procesarse; path: ruta o ubicación utilizada por la operación; semanticName: dato de entrada necesario para la operación; depth: dato de entrada necesario para la operación; budget: dato de entrada necesario para la operación; visited: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void inspect(Object value, String path, String semanticName, int depth, Budget budget, Set<Object> visited) {
        if(value == null) return;
        budget.consume(path);
        if(depth > 24) throw new HydraInputValidationException(path, "Submitted structure is too deeply nested");
        if(value instanceof CharSequence sequence) {
            String text = sequence.toString();
            int limit = isPlainTextField(semanticName) ? properties.getMaxPlainTextCharacters() : properties.getMaxGenericStringCharacters();
            validateStringBudget(path, text, limit);
            if(isPlainTextField(semanticName) && ACTIVE_MARKUP.matcher(text).find()) {
                throw new HydraInputValidationException(path, "Potentially executable markup is not allowed in plain-text fields");
            }
            return;
        }
        if(value instanceof Number || value instanceof Boolean || value instanceof Enum<?> || value instanceof URI) return;
        if(!visited.add(value)) return;
        try {
            if(value instanceof Map<?, ?> map) {
                for(Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    validateStringBudget(path + ".key", key, 512);
                    inspect(entry.getValue(), path + "." + key, key, depth + 1, budget, visited);
                }
                return;
            }
            if(value instanceof Collection<?> collection) {
                int index = 0;
                for(Object item : collection) inspect(item, path + "[" + index++ + "]", semanticName, depth + 1, budget, visited);
                return;
            }
            if(value.getClass().isArray()) {
                int length = java.lang.reflect.Array.getLength(value);
                for(int i = 0; i < length; i++) inspect(java.lang.reflect.Array.get(value, i), path + "[" + i + "]", semanticName, depth + 1, budget, visited);
                return;
            }
            if(value.getClass().isRecord() && value.getClass().getPackageName().startsWith("com.example.editor")) {
                for(RecordComponent component : value.getClass().getRecordComponents()) {
                    try {
                        Object child = component.getAccessor().invoke(value);
                        inspect(child, path + "." + component.getName(), component.getName(), depth + 1, budget, visited);
                    } catch(ReflectiveOperationException exception) {
                        throw new HydraInputValidationException(path, "Could not validate the submitted request structure");
                    }
                }
            }
        } finally {
            visited.remove(value);
        }
    }

    /** Descripción: Evalúa la condición «isPlainTextField» sin modificar el estado externo. Entradas: name: dato de entrada necesario para la operación. Salida: devuelve un indicador que expresa el resultado de la condición. */ private boolean isPlainTextField(String name) {
        return name != null && !EXECUTABLE_FIELD.matcher(name).matches() && PLAIN_FIELD.matcher(name).matches();
    }

    /** Descripción: Valida las condiciones necesarias para «validateStringBudget» y rechaza entradas no válidas. Entradas: field: dato de entrada necesario para la operación; value: valor de entrada que debe procesarse; maxCharacters: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ private void validateStringBudget(String field, String value, int maxCharacters) {
        if(value == null) return;
        if(value.length() > maxCharacters) throw new HydraInputValidationException(field, "The submitted text is too long");
        if(FORBIDDEN_CONTROL.matcher(value).find()) throw new HydraInputValidationException(field, "Control characters are not allowed in this field");
    }

    private static final class Budget {
        private int remaining;
        /** Descripción: Inicializa una instancia de Budget con sus dependencias y datos de entrada. Entradas: maximum: dato de entrada necesario para la operación. Salida: deja la instancia inicializada y lista para su uso. */ Budget(int maximum) { remaining = maximum; }
        /** Descripción: Ejecuta la operación «consume» correspondiente a la responsabilidad de HydraInputPolicy. Entradas: path: ruta o ubicación utilizada por la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ void consume(String path) {
            if(--remaining < 0) throw new HydraInputValidationException(path, "Submitted structure contains too many values");
        }
    }
}
