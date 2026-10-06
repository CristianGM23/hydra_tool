/**
 * Configura una exportación.
 */
export interface MetamodelExportOptions {
    includeLayout: boolean;
    includeStyles: boolean;
    includeWarnings: boolean;
    transparentBackground: boolean;
    imageScale: number;
    codePackageName: string;
}

/**
 * Describe una petición individual de exportación.
 */
export interface MetamodelExportRequest {
    format: string;
    options: MetamodelExportOptions;
}

/**
 * Describe una petición de paquete seleccionado.
 */
export interface MetamodelBundleExportRequest {
    formats: string[];
    options: MetamodelExportOptions;
}
