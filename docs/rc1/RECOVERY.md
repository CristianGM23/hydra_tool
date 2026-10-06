# Recuperación y diagnóstico

## Principio
No repitas automáticamente una escritura fallida si HYDRA no la declara reintentable.

## Procedimiento
1. Lee **qué ocurrió**, **impacto** y **cómo solucionarlo**.
2. Conserva el `correlationId`.
3. Si existe **Actualizar estado**, ejecútalo antes de reintentar.
4. Si la operación quedó `RECOVERY_REQUIRED`, usa el flujo de recuperación del artefacto o journal correspondiente.
5. En errores de credenciales, vuelve a autenticar; no copies secretos al diagnóstico.
6. Si el error persiste, adjunta `correlationId`, versión HYDRA y operación, no un volcado indiscriminado del workspace.
