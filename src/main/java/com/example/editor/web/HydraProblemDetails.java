package com.example.editor.web;

import java.util.List;

/** RFC 9457-compatible envelope con diagnóstico, impacto y recuperación transversal de HYDRA. */
public record HydraProblemDetails(String type,String title,int status,String detail,String instance,String correlationId,
        String code,String message,String field,String reason,String solution,List<String> suggestions,String technicalDetail,
        String operation,String affectedResource,String impact,boolean retryable,List<HydraRecoveryAction> recoveryActions) { }
