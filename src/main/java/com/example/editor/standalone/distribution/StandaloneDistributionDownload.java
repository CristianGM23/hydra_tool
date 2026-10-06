package com.example.editor.standalone.distribution;

/** Binary download created after successful distribution validation. */
public record StandaloneDistributionDownload(String fileName, String sha256, byte[] content) {
    public StandaloneDistributionDownload {
        if(fileName == null || fileName.isBlank()){throw new IllegalArgumentException("fileName is required");}
        if(sha256 == null || sha256.isBlank()){throw new IllegalArgumentException("sha256 is required");}
        content = content == null ? new byte[0] : content.clone();
    }
    /** Descripción: Ejecuta la operación «content» correspondiente a la responsabilidad de StandaloneDistributionDownload. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ @Override public byte[] content(){return content.clone();}
}
