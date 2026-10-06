package com.example.editor.remote.auth.provider;

import com.example.editor.remote.auth.RemoteCredentialMaterial;

/** Resultado de sondeo sin exponer el device_code fuera del backend. */
public record RemoteDevicePollResult(RemoteDevicePollStatus status,RemoteCredentialMaterial credential,int pollIntervalSeconds) {
    /** Descripción: Ejecuta la operación «pending» correspondiente a la responsabilidad de RemoteDevicePollResult. Entradas: seconds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static RemoteDevicePollResult pending(int seconds){return new RemoteDevicePollResult(RemoteDevicePollStatus.PENDING,null,seconds);}
    /** Descripción: Ejecuta la operación «slowDown» correspondiente a la responsabilidad de RemoteDevicePollResult. Entradas: seconds: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static RemoteDevicePollResult slowDown(int seconds){return new RemoteDevicePollResult(RemoteDevicePollStatus.SLOW_DOWN,null,seconds);}
    /** Descripción: Ejecuta la operación «authorized» correspondiente a la responsabilidad de RemoteDevicePollResult. Entradas: credential: dato de entrada necesario para la operación. Salida: devuelve el resultado definido por el contrato del método. */ public static RemoteDevicePollResult authorized(RemoteCredentialMaterial credential){return new RemoteDevicePollResult(RemoteDevicePollStatus.AUTHORIZED,credential,0);}
    /** Descripción: Ejecuta la operación «denied» correspondiente a la responsabilidad de RemoteDevicePollResult. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static RemoteDevicePollResult denied(){return new RemoteDevicePollResult(RemoteDevicePollStatus.DENIED,null,0);}
    /** Descripción: Ejecuta la operación «expired» correspondiente a la responsabilidad de RemoteDevicePollResult. Entradas: no requiere parámetros de entrada. Salida: devuelve el resultado definido por el contrato del método. */ public static RemoteDevicePollResult expired(){return new RemoteDevicePollResult(RemoteDevicePollStatus.EXPIRED,null,0);}
}
