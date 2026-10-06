package com.example.editor.system;

import java.time.Instant;
import com.example.editor.audit.AuditPipelineStatus;

/** Snapshot local de recursos acotados; no contiene rutas, credenciales ni datos de proyecto. */
public record RuntimeResourceStatus(Instant observedAt,long heapUsedBytes,long heapCommittedBytes,long heapMaximumBytes,
        int liveThreads,int daemonThreads,int peakThreads,AuditPipelineStatus audit,int unsavedWorkingCopies,
        int remoteTransfersAvailable,int remoteTransfersMaximum,int pendingExternalAuthorizations,int pendingExternalAuthorizationsMaximum,
        int nativeProcessesAvailable,int nativeProcessesMaximum,int pythonProcessesAvailable,int pythonProcessesMaximum) { }
