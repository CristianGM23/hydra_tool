package com.example.editor.remote.storage;

/**
 * Escritura neutral resuelta contra un perfil server-side. El contenido es un
 * buffer de transferencia interno y se toma en propiedad para evitar copias de
 * decenas de MiB entre la fachada y el adaptador tecnológico.
 */
public record RemoteArtifactWriteCommand(String path,byte[] content,String contentType,RemoteWriteMode mode,String expectedVersion,String commitMessage) {
    public RemoteArtifactWriteCommand {
        path=path==null?"":path.trim();
        content=content==null?new byte[0]:content;
        contentType=contentType==null||contentType.isBlank()?"application/octet-stream":contentType.trim();
        mode=mode==null?RemoteWriteMode.UPSERT:mode;
        expectedVersion=expectedVersion==null?"":expectedVersion.trim();
        commitMessage=commitMessage==null||commitMessage.isBlank()?"Update HYDRA artifact":commitMessage.trim();
    }
    /** Descripción: Ejecuta la operación «contentLength» correspondiente a la responsabilidad de RemoteArtifactWriteCommand. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int contentLength(){return content.length;}
}
