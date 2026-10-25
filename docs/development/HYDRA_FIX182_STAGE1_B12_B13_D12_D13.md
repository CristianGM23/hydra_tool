# HYDRA Fix182 — Primer incremento B12+B13+D12+D13

**Base real:** Fix180 B8+B9+D8+D9 con hotfix WinError 5, 4219 archivos canónicos.
**Dependencia:** Fix181 fue especificado, pero NO se ha implementado en esta rama. Fix182 etapa 1 se construye directamente sobre Fix180; NO sustituye ni certifica el alcance de Fix181.

## B12 — Sirius Desktop y Eclipse GLSP

Los adaptadores reales de Sirius Desktop y Eclipse GLSP producen `META-INF/hydra-fidelity-obligations.json` en cada proyecto exportado. El informe enumera todos los artefactos de entrada con su SHA-256, clasifica las representaciones `DIAGRAM` como `GENERATED_DIAGRAM_UNVERIFIED` y las restantes como `CANONICAL_ONLY`, y obliga a realizar pruebas oficiales independientes antes de declarar paridad. `nativeRuntimeExecuted=false` y `functionalParityCertified=false` en el momento de generación. Los diagnósticos preexistentes de vistas no diagramáticas se conservan.

**NOT_CERTIFIED:** todavía no hay ejecución oficial de Sirius/GLSP procedente de un ZIP exportado por el servicio real de HYDRA, ni tests semánticos cliente+servidor GLSP en runners independientes.

## B13 — Round-trip reforzado de los proyectos externos

`RoundTripSafePaths` verifica rutas de origen, ancestros y destinos y rechaza enlaces simbólicos. El resolvedor rechaza aplicar un plan preparado antes de que el usuario cambie el fichero; no sobrescribe directorios de backup de operaciones anteriores. El journal conserva hashes anteriores y posteriores. El rollback solo restaura datos si las copias de seguridad y los archivos generados conservan sus hashes. En caso contrario, bloquea la operación para evitar destruir cambios del usuario.

Quedan pendientes pruebas integradas con proyectos oficiales Xtext, ANTLR, BESSER, Sirius y GLSP. La operación no ofrece garantías universales frente a carreras entre procesos externos no cooperativos.

## D12 — Evidencias de publicación realmente vinculadas

`scripts/distribution/fix182_native_release_gate.py` exige para cada tecnología o SO solicitado un informe de ejecución específico, huella del paquete, log real con SHA-256 y firmas Ed25519 sobre el reporte completo, con claves públicas y umbral fijados fuera del repositorio. Falta de toolchain, pérdida de observable, actualización no firmada, logs modificados o un destino sin acreditar bloquean la publicación. Las firmas acreditan procedencia de la declaración de un runner confiable; **no prueban mágicamente que esa declaración sea verdadera**. Las pruebas E2E de cada runner deberán generar esos informes.

## D13 — Centro de distribución

`NativeEvidenceStatusPanel` admite la carga local y muestra los estados del informe del release gate. Es un visor de solo lectura: no valida firmas por sí mismo y no autoriza publicar. La decisión corresponde al CLI/CI con política de confianza externa.

## Límites de certificación (en vigor)

- Sin Maven oficial disponible en el entorno de preparación, la compilación Java completa queda **NOT_CERTIFIED** y debe ejecutarse en Windows o CI.
- Sin ejecución nativa real de Sirius/GLSP/Xtext/ANTLR/BESSER, la paridad externa queda **NOT_CERTIFIED**.
- Sin instalar, lanzar, editar, guardar, reiniciar y actualizar con el updater firmado Tauri en Windows/Linux/macOS, ese ciclo queda **NOT_CERTIFIED**.
- Los tests Java21 aislados y los tests Python de firmas verifican exclusivamente sus respectivos contratos focalizados.
- Los bloques y requisitos de Fix181 siguen pendientes y no deben representarse como realizados.

## Validación

`powershell -ExecutionPolicy Bypass -File .\scripts\validation\validate_fix182_stage1.ps1 -StructuralOnly`

Sin `-StructuralOnly`, el wrapper heredará una sola cualificación global al final. Actualizar el manifiesto antes de aplicarlo. No incluir ni sobrescribir Archify.
