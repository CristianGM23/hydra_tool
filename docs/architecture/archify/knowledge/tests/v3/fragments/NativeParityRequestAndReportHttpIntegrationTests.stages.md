Prueba -> etapa (java:com.example.editor.integration.parity.NativeParityRequestAndReportHttpIntegrationTests)
- integration_native_parity.req: invalidCapabilityListsAreRejectedWithoutCompilingOrCreatingWorkspaces (lineas 112-119: lista vacia, repetida, desconocida y de 16 elementos dan 400, cero compilaciones y carpetas de trabajo sin cambios).
- integration_native_parity.to: timeoutIsDefaultedCappedAndRejectedBeforeCompiling (130-131: 0 y -1 valen 180 en la peticion; 137-141: 901 da 400 sin compilar, 900 y 0 dan 200). El efecto del plazo efectivo en la ejecucion no se observa.
- integration_native_parity.cmp: fifteenCapabilitiesInOneRequestCompileTheProjectExactlyOnce (153-155: una sola compilacion para quince capacidades, todas informadas en orden).
- integration_native_parity.ko: uncertifiedReportNamesTheUnverifiedAndTheFailedCapability (166-178: certificado falso, cero demostradas, GRAPHICAL_EDITOR sin verificar y NATIVE_BUILD fallida con su motivo literal).
