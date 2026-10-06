package com.example.editor.metamodel.importing;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configura el sistema de importación.
 */
@Component
@ConfigurationProperties(prefix = "dsl.imports.metamodel")
public class MetamodelImportProperties {

    private boolean enabled = true;
    private long maxFileSizeBytes = 10_485_760;
    private boolean autoDetect = true;
    private boolean strictByDefault = false;
    private int layoutColumns = 4;
    private double layoutHorizontalGap = 70;
    private double layoutVerticalGap = 70;
    private int orthogonalRoutingClassThreshold = 14;
    private int analysisCorePoolSize = 2;
    private int analysisMaxPoolSize = 4;
    private int analysisQueueCapacity = 20;
    private int analysisRetentionMinutes = 20;
    private int analysisMaxRetainedJobs = 64;
    private int analysisMaximumActivityMessages = 2000;
    private int analysisMaximumActivityDetailCharacters = 8192;
    private int analysisMaximumStageLogCharacters = 262144;
    private int analysisMaximumDurationSeconds = 180;
    private int analysisWatchdogPoolSize = 1;
    private int analysisShutdownWaitSeconds = 20;
    private Path stagingRoot = Path.of("./data/metamodel-import-staging");
    private int maxArchiveEntries = 5000;
    private long maxArchiveUncompressedBytes = 104_857_600;
    private int maxNestedArchiveDepth = 4;
    private double maxArchiveCompressionRatio = 150.0;
    private long maxArchiveReadMillis = 30_000L;
    private long maxSourceFileSizeBytes = 5_242_880;
    private int maxSourceFiles = 2000;
    private List<String> allowedFormats = new ArrayList<>(List.of("ecore", "emf", "xmi", "json", "xml", "drawio", "langium", "xtext", "antlr", "java", "typescript", "javascript", "go", "python", "glsp", "plantuml", "markdown", "markdown-agent"));

    /** Descripción: Evalúa la condición «isEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isEnabled() {
        return enabled;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setEnabled». Entradas: enabled: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** Descripción: Obtiene la información correspondiente a «getMaxFileSizeBytes» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setMaxFileSizeBytes». Entradas: maxFileSizeBytes: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    /** Descripción: Evalúa la condición «isAutoDetect» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isAutoDetect() {
        return autoDetect;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setAutoDetect». Entradas: autoDetect: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAutoDetect(boolean autoDetect) {
        this.autoDetect = autoDetect;
    }

    /** Descripción: Evalúa la condición «isStrictByDefault» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isStrictByDefault() {
        return strictByDefault;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setStrictByDefault». Entradas: strictByDefault: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setStrictByDefault(boolean strictByDefault) {
        this.strictByDefault = strictByDefault;
    }

    /** Descripción: Obtiene la información correspondiente a «getLayoutColumns» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getLayoutColumns() {
        return layoutColumns;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setLayoutColumns». Entradas: layoutColumns: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setLayoutColumns(int layoutColumns) {
        this.layoutColumns = layoutColumns;
    }

    /** Descripción: Obtiene la información correspondiente a «getLayoutHorizontalGap» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public double getLayoutHorizontalGap() {
        return layoutHorizontalGap;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setLayoutHorizontalGap». Entradas: layoutHorizontalGap: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setLayoutHorizontalGap(double layoutHorizontalGap) {
        this.layoutHorizontalGap = layoutHorizontalGap;
    }

    /** Descripción: Obtiene la información correspondiente a «getLayoutVerticalGap» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public double getLayoutVerticalGap() {
        return layoutVerticalGap;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setLayoutVerticalGap». Entradas: layoutVerticalGap: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setLayoutVerticalGap(double layoutVerticalGap) {
        this.layoutVerticalGap = layoutVerticalGap;
    }


    /** Descripción: Obtiene la información correspondiente a «getOrthogonalRoutingClassThreshold» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getOrthogonalRoutingClassThreshold() {
        return orthogonalRoutingClassThreshold;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setOrthogonalRoutingClassThreshold». Entradas: orthogonalRoutingClassThreshold: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setOrthogonalRoutingClassThreshold(int orthogonalRoutingClassThreshold) {
        this.orthogonalRoutingClassThreshold = Math.max(0, orthogonalRoutingClassThreshold);
    }

    /** Descripción: Obtiene la información correspondiente a «getAnalysisCorePoolSize» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getAnalysisCorePoolSize() {
        return analysisCorePoolSize;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setAnalysisCorePoolSize». Entradas: analysisCorePoolSize: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAnalysisCorePoolSize(int analysisCorePoolSize) {
        this.analysisCorePoolSize = analysisCorePoolSize;
    }

    /** Descripción: Obtiene la información correspondiente a «getAnalysisMaxPoolSize» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getAnalysisMaxPoolSize() {
        return analysisMaxPoolSize;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setAnalysisMaxPoolSize». Entradas: analysisMaxPoolSize: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAnalysisMaxPoolSize(int analysisMaxPoolSize) {
        this.analysisMaxPoolSize = analysisMaxPoolSize;
    }

    /** Descripción: Obtiene la información correspondiente a «getAnalysisQueueCapacity» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getAnalysisQueueCapacity() {
        return analysisQueueCapacity;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setAnalysisQueueCapacity». Entradas: analysisQueueCapacity: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAnalysisQueueCapacity(int analysisQueueCapacity) {
        this.analysisQueueCapacity = analysisQueueCapacity;
    }

    /** Descripción: Obtiene la información correspondiente a «getAnalysisRetentionMinutes» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getAnalysisRetentionMinutes() {
        return analysisRetentionMinutes;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setAnalysisRetentionMinutes». Entradas: analysisRetentionMinutes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAnalysisRetentionMinutes(int analysisRetentionMinutes) {
        this.analysisRetentionMinutes = analysisRetentionMinutes;
    }

    /** Descripción: Obtiene la información correspondiente a «getAnalysisMaxRetainedJobs» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getAnalysisMaxRetainedJobs(){return analysisMaxRetainedJobs;}
    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setAnalysisMaxRetainedJobs». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAnalysisMaxRetainedJobs(int value){analysisMaxRetainedJobs = Math.max(10, value);}
    /** Descripción: Obtiene la información correspondiente a «getAnalysisMaximumActivityMessages» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getAnalysisMaximumActivityMessages(){return analysisMaximumActivityMessages;}
    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setAnalysisMaximumActivityMessages». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAnalysisMaximumActivityMessages(int value){analysisMaximumActivityMessages = Math.max(100, value);}
    /** Descripción: Obtiene la información correspondiente a «getAnalysisMaximumActivityDetailCharacters» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getAnalysisMaximumActivityDetailCharacters(){return analysisMaximumActivityDetailCharacters;}
    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setAnalysisMaximumActivityDetailCharacters». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAnalysisMaximumActivityDetailCharacters(int value){analysisMaximumActivityDetailCharacters = Math.max(512, value);}
    /** Descripción: Obtiene la información correspondiente a «getAnalysisMaximumStageLogCharacters» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getAnalysisMaximumStageLogCharacters(){return analysisMaximumStageLogCharacters;}
    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setAnalysisMaximumStageLogCharacters». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAnalysisMaximumStageLogCharacters(int value){analysisMaximumStageLogCharacters = Math.max(16_384, value);}



    /** Duración total máxima de un análisis, incluido el tiempo en cola. */ /** Descripción: Obtiene la información correspondiente a «getAnalysisMaximumDurationSeconds» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getAnalysisMaximumDurationSeconds(){return analysisMaximumDurationSeconds;}
    /** Configura la duración total máxima de un análisis. */ /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setAnalysisMaximumDurationSeconds». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAnalysisMaximumDurationSeconds(int value){analysisMaximumDurationSeconds=Math.max(1,Math.min(86_400,value));}
    /** Número de hilos del watchdog de análisis. */ /** Descripción: Obtiene la información correspondiente a «getAnalysisWatchdogPoolSize» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getAnalysisWatchdogPoolSize(){return analysisWatchdogPoolSize;}
    /** Configura el número acotado de hilos del watchdog. */ /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setAnalysisWatchdogPoolSize». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAnalysisWatchdogPoolSize(int value){analysisWatchdogPoolSize=Math.max(1,Math.min(8,value));}
    /** Espera máxima al cerrar el ejecutor de análisis. */ /** Descripción: Obtiene la información correspondiente a «getAnalysisShutdownWaitSeconds» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getAnalysisShutdownWaitSeconds(){return analysisShutdownWaitSeconds;}
    /** Configura la espera máxima al cerrar el ejecutor de análisis. */ /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setAnalysisShutdownWaitSeconds». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAnalysisShutdownWaitSeconds(int value){analysisShutdownWaitSeconds=Math.max(1,Math.min(300,value));}

    /** Descripción: Obtiene la información correspondiente a «getStagingRoot» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path getStagingRoot() {
        return stagingRoot;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setStagingRoot». Entradas: stagingRoot: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setStagingRoot(Path stagingRoot) {
        this.stagingRoot = stagingRoot == null ? Path.of("./data/metamodel-import-staging") : stagingRoot;
    }

    /** Descripción: Obtiene la información correspondiente a «getMaxArchiveEntries» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaxArchiveEntries() {
        return maxArchiveEntries;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setMaxArchiveEntries». Entradas: maxArchiveEntries: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaxArchiveEntries(int maxArchiveEntries) {
        this.maxArchiveEntries = Math.max(1, maxArchiveEntries);
    }

    /** Descripción: Obtiene la información correspondiente a «getMaxArchiveUncompressedBytes» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getMaxArchiveUncompressedBytes() {
        return maxArchiveUncompressedBytes;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setMaxArchiveUncompressedBytes». Entradas: maxArchiveUncompressedBytes: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaxArchiveUncompressedBytes(long maxArchiveUncompressedBytes) {
        this.maxArchiveUncompressedBytes = Math.max(1, maxArchiveUncompressedBytes);
    }

    /** Profundidad máxima de ZIP/JAR/WAR/EAR anidados. */ /** Descripción: Obtiene la información correspondiente a «getMaxNestedArchiveDepth» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaxNestedArchiveDepth() { return maxNestedArchiveDepth; }
    /** Configura la profundidad máxima de contenedores anidados. */ /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setMaxNestedArchiveDepth». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaxNestedArchiveDepth(int value) { maxNestedArchiveDepth = Math.max(0, value); }
    /** Ratio máximo de expansión aceptado durante el análisis. */ /** Descripción: Obtiene la información correspondiente a «getMaxArchiveCompressionRatio» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public double getMaxArchiveCompressionRatio() { return maxArchiveCompressionRatio; }
    /** Configura el ratio máximo de expansión aceptado. */ /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setMaxArchiveCompressionRatio». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaxArchiveCompressionRatio(double value) { maxArchiveCompressionRatio = Math.max(1D, value); }
    /** Tiempo máximo de lectura de un archivo de importación. */ /** Descripción: Obtiene la información correspondiente a «getMaxArchiveReadMillis» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getMaxArchiveReadMillis() { return maxArchiveReadMillis; }
    /** Configura el tiempo máximo de lectura de archivos de importación. */ /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setMaxArchiveReadMillis». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaxArchiveReadMillis(long value) { maxArchiveReadMillis = Math.max(1000L, value); }

    /** Descripción: Obtiene la información correspondiente a «getMaxSourceFileSizeBytes» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getMaxSourceFileSizeBytes() {
        return maxSourceFileSizeBytes;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setMaxSourceFileSizeBytes». Entradas: maxSourceFileSizeBytes: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaxSourceFileSizeBytes(long maxSourceFileSizeBytes) {
        this.maxSourceFileSizeBytes = Math.max(1, maxSourceFileSizeBytes);
    }

    /** Descripción: Obtiene la información correspondiente a «getMaxSourceFiles» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaxSourceFiles() {
        return maxSourceFiles;
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setMaxSourceFiles». Entradas: maxSourceFiles: archivo o contenido que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaxSourceFiles(int maxSourceFiles) {
        this.maxSourceFiles = Math.max(1, maxSourceFiles);
    }

    /** Descripción: Obtiene la información correspondiente a «getAllowedFormats» dentro de MetamodelImportProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> getAllowedFormats() {
        return List.copyOf(allowedFormats);
    }

    /** Descripción: Actualiza el estado gestionado por MetamodelImportProperties mediante la operación «setAllowedFormats». Entradas: allowedFormats: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setAllowedFormats(List<String> allowedFormats) {
        this.allowedFormats = allowedFormats == null ? new ArrayList<>() : new ArrayList<>(allowedFormats);
    }
}
