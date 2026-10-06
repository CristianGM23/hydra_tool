package com.example.editor.transformation.nativeexec;

/** Request to install one package already present in the trusted server-side catalog. */
public record TransformationToolchainProvisioningRequest(
        String packageId,
        String packageVersion,
        String expectedCurrentPackageId,
        String expectedCurrentPackageVersion) {
    public TransformationToolchainProvisioningRequest {
        packageId = clean(packageId);
        packageVersion = clean(packageVersion);
        expectedCurrentPackageId = clean(expectedCurrentPackageId);
        expectedCurrentPackageVersion = clean(expectedCurrentPackageVersion);
    }

    /** Normalizes optional request metadata. */
    /** Descripción: Ejecuta la operación «clean» correspondiente a la responsabilidad de TransformationToolchainProvisioningRequest. Entradas: value: valor de entrada que debe procesarse. Salida: devuelve el resultado definido por el contrato del método. */ private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
