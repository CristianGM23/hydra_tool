package com.example.editor.audit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuración centralizada del subsistema de actividad/auditoría de HYDRA.
 * Todos los límites están acotados para evitar colas, respuestas o diffs sin límite.
 */
@Component
@ConfigurationProperties(prefix = "hydra.audit")
public class AuditLogProperties {
    private boolean enabled = true;
    private String storageProvider = "local-jsonl";
    private Path storageRoot = Path.of("data", "audit-log");
    private boolean retentionEnabled = true;
    private int retentionDays = 180;
    private int navigationRetentionDays = 7;
    private int openRetentionDays = 14;
    private int routineRetentionDays = 60;
    private long maximumStorageBytes = 134217728L;
    private int queueCapacity = 2048;
    private int batchSize = 64;
    private long flushIntervalMillis = 300L;
    private int writeRetries = 2;
    private long writeTimeoutMillis = 5000L;
    private long shutdownWaitMillis = 3000L;
    private long shutdownFlushLockMillis = 250L;
    private String overflowPolicy = "SYNC_FALLBACK";
    private boolean dropLowValueOnOverflow = true;
    private int maximumChanges = 100;
    private int maximumValueLength = 2048;
    private int maximumMetadataEntries = 64;
    private int maximumSnapshotNodes = 20000;
    private int maximumSnapshotBytes = 4194304;
    private int defaultPageSize = 50;
    private int maximumPageSize = 250;
    private int defaultQueryDays = 30;
    private long maximumExportEntries = 250000L;
    private int statisticsCacheEntries = 32;
    private long statisticsCacheMillis = 2000L;
    private boolean captureReadActions = false;
    private boolean captureOpenActions = true;
    private boolean captureHttpMutationActions = true;
    private boolean captureNavigationActions = true;
    private long clientDedupeWindowMillis = 750L;
    private int clientDedupeEntries = 1024;
    private int maximumClientStringLength = 512;
    private final List<String> ignoredNamespaces = new ArrayList<>(List.of(
            "event.flow.runtime", "gamification.runtime.ledger", "gamification.runtime.state",
            "projection.manifest", "test.documents"));
    private final List<String> sensitiveKeys = new ArrayList<>(List.of(
            "password", "passwd", "secret", "token", "authorization", "credential", "privatekey",
            "apikey", "api_key", "connectionstring", "connection_string"));

    /** Descripción: Evalúa la condición «isEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isEnabled() { return enabled; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setEnabled». Entradas: enabled: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setEnabled(boolean enabled) { this.enabled = enabled; }
    /** Descripción: Obtiene la información correspondiente a «getStorageProvider» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getStorageProvider() { return storageProvider; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setStorageProvider». Entradas: storageProvider: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setStorageProvider(String storageProvider) { this.storageProvider = storageProvider; }
    /** Descripción: Obtiene la información correspondiente a «getStorageRoot» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public Path getStorageRoot() { return storageRoot; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setStorageRoot». Entradas: storageRoot: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setStorageRoot(Path storageRoot) { this.storageRoot = storageRoot; }
    /** Descripción: Evalúa la condición «isRetentionEnabled» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isRetentionEnabled() { return retentionEnabled; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setRetentionEnabled». Entradas: retentionEnabled: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setRetentionEnabled(boolean retentionEnabled) { this.retentionEnabled = retentionEnabled; }
    /** Descripción: Obtiene la información correspondiente a «getRetentionDays» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getRetentionDays() { return retentionDays; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setRetentionDays». Entradas: retentionDays: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setRetentionDays(int retentionDays) { this.retentionDays = positive(retentionDays, "retentionDays"); }
    /** Descripción: Obtiene la información correspondiente a «getNavigationRetentionDays» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getNavigationRetentionDays() { return navigationRetentionDays; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setNavigationRetentionDays». Entradas: navigationRetentionDays: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setNavigationRetentionDays(int navigationRetentionDays) { this.navigationRetentionDays = positive(navigationRetentionDays, "navigationRetentionDays"); }
    /** Descripción: Obtiene la información correspondiente a «getOpenRetentionDays» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getOpenRetentionDays() { return openRetentionDays; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setOpenRetentionDays». Entradas: openRetentionDays: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setOpenRetentionDays(int openRetentionDays) { this.openRetentionDays = positive(openRetentionDays, "openRetentionDays"); }
    /** Descripción: Obtiene la información correspondiente a «getRoutineRetentionDays» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getRoutineRetentionDays() { return routineRetentionDays; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setRoutineRetentionDays». Entradas: routineRetentionDays: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setRoutineRetentionDays(int routineRetentionDays) { this.routineRetentionDays = positive(routineRetentionDays, "routineRetentionDays"); }
    /** Descripción: Obtiene la información correspondiente a «getMaximumStorageBytes» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getMaximumStorageBytes() { return maximumStorageBytes; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setMaximumStorageBytes». Entradas: maximumStorageBytes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumStorageBytes(long maximumStorageBytes) { this.maximumStorageBytes = positive(maximumStorageBytes, "maximumStorageBytes"); }
    /** Descripción: Obtiene la información correspondiente a «getQueueCapacity» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getQueueCapacity() { return queueCapacity; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setQueueCapacity». Entradas: queueCapacity: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setQueueCapacity(int queueCapacity) { this.queueCapacity = positive(queueCapacity, "queueCapacity"); }
    /** Descripción: Obtiene la información correspondiente a «getBatchSize» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getBatchSize() { return batchSize; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setBatchSize». Entradas: batchSize: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setBatchSize(int batchSize) { this.batchSize = positive(batchSize, "batchSize"); }
    /** Descripción: Obtiene la información correspondiente a «getFlushIntervalMillis» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getFlushIntervalMillis() { return flushIntervalMillis; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setFlushIntervalMillis». Entradas: flushIntervalMillis: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setFlushIntervalMillis(long flushIntervalMillis) { this.flushIntervalMillis = positive(flushIntervalMillis, "flushIntervalMillis"); }
    /** Descripción: Obtiene la información correspondiente a «getWriteRetries» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getWriteRetries() { return writeRetries; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setWriteRetries». Entradas: writeRetries: componente de conversión utilizado durante el procesamiento. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setWriteRetries(int writeRetries) { this.writeRetries = boundedInt(writeRetries, 0, 10, "writeRetries"); }
    /** Descripción: Obtiene el tiempo máximo permitido para una escritura física de auditoría. Entradas: no requiere parámetros. Salida: devuelve el límite en milisegundos. */ public long getWriteTimeoutMillis() { return writeTimeoutMillis; }
    /** Descripción: Configura el tiempo máximo de una escritura física de auditoría. Entradas: writeTimeoutMillis: límite positivo y defensivamente acotado. Salida: actualiza la configuración. */ public void setWriteTimeoutMillis(long writeTimeoutMillis) { this.writeTimeoutMillis = boundedMillis(writeTimeoutMillis, 100L, 120000L, "writeTimeoutMillis"); }
    /** Descripción: Obtiene la espera máxima de finalización de workers de auditoría. Entradas: no requiere parámetros. Salida: devuelve el límite en milisegundos. */ public long getShutdownWaitMillis() { return shutdownWaitMillis; }
    /** Descripción: Configura la espera máxima de finalización de workers de auditoría. Entradas: shutdownWaitMillis: límite positivo y acotado. Salida: actualiza la configuración. */ public void setShutdownWaitMillis(long shutdownWaitMillis) { this.shutdownWaitMillis = boundedMillis(shutdownWaitMillis, 100L, 30000L, "shutdownWaitMillis"); }
    /** Descripción: Obtiene la espera máxima para adquirir el lock del drenaje final. Entradas: no requiere parámetros. Salida: devuelve el límite en milisegundos. */ public long getShutdownFlushLockMillis() { return shutdownFlushLockMillis; }
    /** Descripción: Configura la espera máxima para adquirir el lock del drenaje final. Entradas: shutdownFlushLockMillis: límite positivo y acotado. Salida: actualiza la configuración. */ public void setShutdownFlushLockMillis(long shutdownFlushLockMillis) { this.shutdownFlushLockMillis = boundedMillis(shutdownFlushLockMillis, 10L, 5000L, "shutdownFlushLockMillis"); }
    /** Descripción: Obtiene la información correspondiente a «getOverflowPolicy» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public String getOverflowPolicy() { return overflowPolicy; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setOverflowPolicy». Entradas: overflowPolicy: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setOverflowPolicy(String overflowPolicy) { this.overflowPolicy = requireText(overflowPolicy, "overflowPolicy"); }
    /** Descripción: Evalúa la condición «isDropLowValueOnOverflow» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isDropLowValueOnOverflow() { return dropLowValueOnOverflow; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setDropLowValueOnOverflow». Entradas: value: valor de entrada que debe procesarse. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setDropLowValueOnOverflow(boolean value) { this.dropLowValueOnOverflow = value; }
    /** Descripción: Obtiene la información correspondiente a «getMaximumChanges» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumChanges() { return maximumChanges; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setMaximumChanges». Entradas: maximumChanges: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumChanges(int maximumChanges) { this.maximumChanges = positive(maximumChanges, "maximumChanges"); }
    /** Descripción: Obtiene la información correspondiente a «getMaximumValueLength» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumValueLength() { return maximumValueLength; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setMaximumValueLength». Entradas: maximumValueLength: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumValueLength(int maximumValueLength) { this.maximumValueLength = positive(maximumValueLength, "maximumValueLength"); }
    /** Descripción: Obtiene la información correspondiente a «getMaximumMetadataEntries» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumMetadataEntries() { return maximumMetadataEntries; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setMaximumMetadataEntries». Entradas: maximumMetadataEntries: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumMetadataEntries(int maximumMetadataEntries) { this.maximumMetadataEntries = positive(maximumMetadataEntries, "maximumMetadataEntries"); }
    /** Descripción: Obtiene la información correspondiente a «getMaximumSnapshotNodes» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumSnapshotNodes() { return maximumSnapshotNodes; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setMaximumSnapshotNodes». Entradas: maximumSnapshotNodes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumSnapshotNodes(int maximumSnapshotNodes) { this.maximumSnapshotNodes = positive(maximumSnapshotNodes, "maximumSnapshotNodes"); }
    /** Descripción: Obtiene la información correspondiente a «getMaximumSnapshotBytes» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumSnapshotBytes() { return maximumSnapshotBytes; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setMaximumSnapshotBytes». Entradas: maximumSnapshotBytes: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumSnapshotBytes(int maximumSnapshotBytes) { this.maximumSnapshotBytes = positive(maximumSnapshotBytes, "maximumSnapshotBytes"); }
    /** Descripción: Obtiene la información correspondiente a «getDefaultPageSize» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getDefaultPageSize() { return defaultPageSize; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setDefaultPageSize». Entradas: defaultPageSize: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setDefaultPageSize(int defaultPageSize) { this.defaultPageSize = positive(defaultPageSize, "defaultPageSize"); }
    /** Descripción: Obtiene la información correspondiente a «getMaximumPageSize» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumPageSize() { return maximumPageSize; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setMaximumPageSize». Entradas: maximumPageSize: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumPageSize(int maximumPageSize) { this.maximumPageSize = positive(maximumPageSize, "maximumPageSize"); }
    /** Descripción: Obtiene la información correspondiente a «getDefaultQueryDays» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getDefaultQueryDays() { return defaultQueryDays; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setDefaultQueryDays». Entradas: defaultQueryDays: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setDefaultQueryDays(int defaultQueryDays) { this.defaultQueryDays = positive(defaultQueryDays, "defaultQueryDays"); }
    /** Descripción: Obtiene la información correspondiente a «getMaximumExportEntries» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getMaximumExportEntries() { return maximumExportEntries; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setMaximumExportEntries». Entradas: maximumExportEntries: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumExportEntries(long maximumExportEntries) { this.maximumExportEntries = positive(maximumExportEntries, "maximumExportEntries"); }
    /** Descripción: Obtiene la información correspondiente a «getStatisticsCacheEntries» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getStatisticsCacheEntries() { return statisticsCacheEntries; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setStatisticsCacheEntries». Entradas: statisticsCacheEntries: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setStatisticsCacheEntries(int statisticsCacheEntries) { this.statisticsCacheEntries = positive(statisticsCacheEntries, "statisticsCacheEntries"); }
    /** Descripción: Obtiene la información correspondiente a «getStatisticsCacheMillis» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getStatisticsCacheMillis() { return statisticsCacheMillis; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setStatisticsCacheMillis». Entradas: statisticsCacheMillis: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setStatisticsCacheMillis(long statisticsCacheMillis) { this.statisticsCacheMillis = positive(statisticsCacheMillis, "statisticsCacheMillis"); }
    /** Descripción: Evalúa la condición «isCaptureReadActions» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isCaptureReadActions() { return captureReadActions; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setCaptureReadActions». Entradas: captureReadActions: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setCaptureReadActions(boolean captureReadActions) { this.captureReadActions = captureReadActions; }
    /** Descripción: Evalúa la condición «isCaptureOpenActions» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isCaptureOpenActions() { return captureOpenActions; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setCaptureOpenActions». Entradas: captureOpenActions: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setCaptureOpenActions(boolean captureOpenActions) { this.captureOpenActions = captureOpenActions; }
    /** Descripción: Evalúa la condición «isCaptureHttpMutationActions» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isCaptureHttpMutationActions() { return captureHttpMutationActions; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setCaptureHttpMutationActions». Entradas: captureHttpMutationActions: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setCaptureHttpMutationActions(boolean captureHttpMutationActions) { this.captureHttpMutationActions = captureHttpMutationActions; }
    /** Descripción: Evalúa la condición «isCaptureNavigationActions» sin modificar el estado externo. Entradas: no requiere parámetros de entrada. Salida: devuelve un indicador que expresa el resultado de la condición. */ public boolean isCaptureNavigationActions() { return captureNavigationActions; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setCaptureNavigationActions». Entradas: captureNavigationActions: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setCaptureNavigationActions(boolean captureNavigationActions) { this.captureNavigationActions = captureNavigationActions; }
    /** Descripción: Obtiene la información correspondiente a «getClientDedupeWindowMillis» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public long getClientDedupeWindowMillis() { return clientDedupeWindowMillis; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setClientDedupeWindowMillis». Entradas: clientDedupeWindowMillis: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setClientDedupeWindowMillis(long clientDedupeWindowMillis) { this.clientDedupeWindowMillis = nonNegative(clientDedupeWindowMillis, "clientDedupeWindowMillis"); }
    /** Descripción: Obtiene la información correspondiente a «getClientDedupeEntries» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getClientDedupeEntries() { return clientDedupeEntries; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setClientDedupeEntries». Entradas: clientDedupeEntries: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setClientDedupeEntries(int clientDedupeEntries) { this.clientDedupeEntries = positive(clientDedupeEntries, "clientDedupeEntries"); }
    /** Descripción: Obtiene la información correspondiente a «getMaximumClientStringLength» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public int getMaximumClientStringLength() { return maximumClientStringLength; }
    /** Descripción: Actualiza el estado gestionado por AuditLogProperties mediante la operación «setMaximumClientStringLength». Entradas: maximumClientStringLength: dato de entrada necesario para la operación. Salida: no devuelve valor; completa la operación o comunica el fallo mediante el contrato existente. */ public void setMaximumClientStringLength(int maximumClientStringLength) { this.maximumClientStringLength = positive(maximumClientStringLength, "maximumClientStringLength"); }
    /** Descripción: Obtiene la información correspondiente a «getIgnoredNamespaces» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> getIgnoredNamespaces() { return ignoredNamespaces; }
    /** Descripción: Obtiene la información correspondiente a «getSensitiveKeys» dentro de AuditLogProperties. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public List<String> getSensitiveKeys() { return sensitiveKeys; }

    /** Descripción: Ejecuta la operación «positive» correspondiente a la responsabilidad de AuditLogProperties. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static int positive(int value, String field) {
        if(value <= 0) throw new IllegalArgumentException(field + " must be greater than zero");
        return value;
    }
    /** Descripción: Ejecuta la operación «positive» correspondiente a la responsabilidad de AuditLogProperties. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static long positive(long value, String field) {
        if(value <= 0L) throw new IllegalArgumentException(field + " must be greater than zero");
        return value;
    }
    /** Descripción: Ejecuta la operación «nonNegative» correspondiente a la responsabilidad de AuditLogProperties. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static int nonNegative(int value, String field) {
        if(value < 0) throw new IllegalArgumentException(field + " must not be negative");
        return value;
    }
    /** Descripción: Ejecuta la operación «nonNegative» correspondiente a la responsabilidad de AuditLogProperties. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static long nonNegative(long value, String field) {
        if(value < 0L) throw new IllegalArgumentException(field + " must not be negative");
        return value;
    }
    /** Descripción: Valida un timeout positivo dentro de límites operativos defensivos. Entradas: value: valor solicitado; minimum: mínimo; maximum: máximo; field: nombre de propiedad. Salida: devuelve el valor validado. */ private static long boundedMillis(long value, long minimum, long maximum, String field) {
        if(value < minimum || value > maximum) throw new IllegalArgumentException(field + " must be between " + minimum + " and " + maximum + " milliseconds");
        return value;
    }
    /** Descripción: Valida un entero dentro de límites operativos defensivos. Entradas: value: valor solicitado; minimum: mínimo; maximum: máximo; field: nombre de propiedad. Salida: devuelve el valor validado. */ private static int boundedInt(int value, int minimum, int maximum, String field) {
        if(value < minimum || value > maximum) throw new IllegalArgumentException(field + " must be between " + minimum + " and " + maximum);
        return value;
    }
    /** Descripción: Valida las condiciones necesarias para «requireText» y rechaza entradas no válidas. Entradas: value: valor de entrada que debe procesarse; field: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ private static String requireText(String value, String field) {
        if(value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
