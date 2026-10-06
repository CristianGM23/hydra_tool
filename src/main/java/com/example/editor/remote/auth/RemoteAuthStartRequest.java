package com.example.editor.remote.auth;

/**
 * Inicia la autorización para un perfil remoto configurado; no contiene
 * credenciales. El idioma acompaña al flujo para que la página de retorno del
 * proveedor utilice el mismo idioma visible que HYDRA.
 */
public record RemoteAuthStartRequest(String profileId, String language) {
    public RemoteAuthStartRequest {
        profileId = profileId == null ? "" : profileId.trim();
        String requested = language == null ? "" : language.trim().toLowerCase(java.util.Locale.ROOT);
        language = requested.startsWith("es") ? "es" : "en";
    }
}
