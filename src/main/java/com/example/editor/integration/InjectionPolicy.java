package com.example.editor.integration;
import com.fasterxml.jackson.annotation.JsonInclude;
/** Reglas que impiden una inyección destructiva sobre proyectos existentes. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record InjectionPolicy(InjectionStrategy strategy, String generatedRoot, String moduleName, boolean previewRequired,
        boolean rollbackRequired, boolean preserveCustomCode, boolean allowModifyExistingFiles,
        boolean allowModifyGeneratedFiles, boolean failOnPartialMappings) {
    private static final int MAX_GENERATED_ROOT_LENGTH = 240;

    public InjectionPolicy {
        if(strategy == null) strategy = InjectionStrategy.ADD_MODULE;
        generatedRoot = normalizeGeneratedRoot(generatedRoot);
        moduleName = moduleName == null ? "" : moduleName.trim();
        if(strategy == InjectionStrategy.PATCH_EXISTING && !allowModifyExistingFiles) throw new IllegalArgumentException("PATCH_EXISTING requires allowModifyExistingFiles");
    }

    /** Descripción: Ejecuta la operación «normalizeGeneratedRoot» correspondiente a la responsabilidad de InjectionPolicy. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String normalizeGeneratedRoot(String value) {
        String normalized = value == null || value.isBlank() ? "generated-integration" : value.trim().replace('\\', '/');
        while(normalized.contains("//")) normalized = normalized.replace("//", "/");
        if(normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if(normalized.startsWith("./")) normalized = normalized.substring(2);
        if(normalized.isBlank()) throw new IllegalArgumentException("generatedRoot must not be blank");
        if(normalized.length() > MAX_GENERATED_ROOT_LENGTH) throw new IllegalArgumentException("generatedRoot must not exceed 240 characters");
        if(normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*") || normalized.matches("^[A-Za-z][A-Za-z0-9+.-]*://.*")) {
            throw new IllegalArgumentException("generatedRoot must be a project-relative path");
        }
        for(String segment : normalized.split("/", -1)) {
            if(segment.isBlank() || segment.equals(".") || segment.equals("..")) throw new IllegalArgumentException("generatedRoot must remain inside the project and must not contain . or .. path segments");
            String windowsName = segment.toLowerCase(java.util.Locale.ROOT);
            String windowsBase = windowsName.contains(".") ? windowsName.substring(0, windowsName.indexOf('.')) : windowsName;
            boolean windowsReserved = windowsBase.equals("con") || windowsBase.equals("prn") || windowsBase.equals("aux") || windowsBase.equals("nul")
                    || windowsBase.matches("com[1-9]") || windowsBase.matches("lpt[1-9]");
            if(segment.endsWith(".") || segment.endsWith(" ") || windowsReserved) {
                throw new IllegalArgumentException("generatedRoot contains a path segment that is not portable across supported operating systems");
            }
            for(int index = 0; index < segment.length(); index++) {
                char character = segment.charAt(index);
                if(character < 32 || ":*?\"<>|".indexOf(character) >= 0) {
                    throw new IllegalArgumentException("generatedRoot contains characters that are not portable across supported operating systems");
                }
            }
        }
        return normalized;
    }

    /** Descripción: Ejecuta la operación «safeDefaults» correspondiente a la responsabilidad de InjectionPolicy. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static InjectionPolicy safeDefaults() { return new InjectionPolicy(InjectionStrategy.ADD_MODULE, "generated-integration", "", true, true, true, false, false, true); }
}
