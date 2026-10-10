# HYDRA R6C4 Fix180 — B8+B9+D8+D9 en una entrega acumulativa

**Base:** Fix180 etapa 1 sobre Fix179. HYDRA sigue siendo web; los editores generados son independientes.
**Estado honesto:** infraestructura operativa, regresiones del runtime generado, transacciones de migración y pruebas de seguridad PASS en el entorno de preparación. **No están certificados aún EMF/Langium nativos, instaladores Tauri por SO, migraciones en instaladores Tauri ni actualizaciones firmadas por Tauri.** No marcar la versión como release GA hasta la cualificación de Windows y runners de destino.

## B8 — Procedencia comprobable de un proyecto exportado

`scripts/validation/fix180_export_provenance.py` examina el **ZIP emitido por el exportador de proyectos completos existente**. Comprueba el manifiesto `META-INF/hydra-technology-compilation.json`, cada recurso y su hash, la instantánea `META-INF/hydra-project-generation-snapshot.json` y el gate `META-INF/hydra-metamodel-generation-gate.json`. Recalcula la huella generacional siguiendo el contrato Java real de `MetamodelProjectGenerationSnapshotService`, detecta diferencias de identidad, miembros no declarados, rutas peligrosas y colisiones portables de nombres.

Con `--source-inventory`, coteja por SHA-256 **los bytes exactos** de cada artefacto canónico utilizado para la generación. El JSON de inventario debe tener este formato:

```json
{
  "metamodelId": "course",
  "artifacts": [
    {"kind":"METAMODEL","documentId":"course","relativePath":"metamodel-canonical.json"}
  ]
}
```

La ruta relativa se resuelve contra el directorio del inventario. **No se puede fabricar un inventario a partir del ZIP y afirmar que reproduce el proyecto original:** sus entradas tienen que proceder del mismo snapshot canónico, incluida la serialización exacta. El manifest de generación es una afirmación verificable de consistencia, **no una firma criptográfica del exportador**. La política de firma de releases y la certificación semántica son controles distintos.

Ejemplo de verificación de un ZIP real exportado por HYDRA:

```powershell
python .\scripts\validation\fix180_export_provenance.py --zip C:\temp\course-langium-complete.zip --source-inventory C:\temp\sources.json
```

Para ejecutar de verdad los comandos allowlisted del proyecto externo en un **directorio temporal aislado**, solo con fuentes de confianza:

```powershell
python .\scripts\validation\fix180_export_provenance.py --zip C:\temp\course-langium-complete.zip --source-inventory C:\temp\sources.json --native-profile langium --trusted-export --execute-native
```

Sustituir `langium` por `emf` cuando corresponda; requiere `npm`/Maven oficiales y dependencias. El informe diferencia `STRUCTURAL_ONLY`, `NATIVE_BUILD_AND_TESTS` y `NATIVE_BUILD_FAILED`. Incluso cuando todo compila, `functionalParityCertified` permanece **false**: hay que contrastar resultados observables completos con el oráculo original.

## B9 — Comportamientos reales y corpus canónico ampliado

`reference-projects/fix180-advanced-canonical/artifacts/` contiene un modelo canónico de un curso con clases base y derivadas, tipos primitivos, multiplicidades, contención y referencias cruzadas. `fix180_model_oracle.py` valida múltiples invariantes independientes de las tecnologías de destino. Las mutaciones negativas demuestran detección de cardinalidades ausentes y excedidas, unicidad, ciclos, referencias colgantes, tipos, raíces e identidad. Este corpus amplía las siete regresiones heredadas, **pero todavía no agota los metamodelos ni las transformaciones de HYDRA**.

Además, `verify_fix180_runtime_semantics.mjs` ejecuta la fuente de producción `standalone-template/frontend/runtime.js` con documentos canónicos HYDRA reales de biblioteca: restricciones expresivas, Quick Fix, emisión de eventos, puntos limitados por política `ONCE` y relaciones rotas. Es **comportamiento ejecutado del runtime exportable**; no es el motor nativo EMF/Langium, ni demuestra conversión sin pérdidas de estos motores.

Para alcanzar B8/B9 final se requiere por cada destino: snapshot fuente auténtico → generación por el servicio de HYDRA → artefactos vinculados → build oficial real → tests de reglas, operaciones y eventos **en el runtime de destino** → resultados observables comparados → informe de divergencia semántica y trazabilidad. Sin esos pasos, estado `NOT_CERTIFIED`.

## D8 — Ejecución independiente y prueba nativa optativa por SO

`run_native_installer_smoke.py` requiere `--execute` y comprueba que opera en el SO real del runner. En Windows intenta instalar un MSI en una carpeta temporal, en Linux copia un AppImage y lo ejecuta con Xvfb, y en macOS instala una copia local del `.app` y arranca su ejecutable. Si no se ejecuta o no arranca, no emite `PROCESS_LAUNCH_VERIFIED`. **El proceso arrancado no demuestra que el editor soporte guardar modelos**. Tampoco certifica firma del sistema operativo ni actualizador Tauri.

El workflow `.github/workflows/hydra-generated-editor-matrix.yml` expone la opción manual `native_os_smoke`. Es una operación intencionadamente explícita porque **ejecuta instaladores**; se limita a runners efímeros de CI con artefactos de confianza. Sus resultados todavía no se han observado en dichos sistemas durante esta preparación.

## D9 — Migración real y reversible de datos, sin código arbitrario

`scripts/distribution/migrate_standalone_models.py` recibe un plan declarativo (versiones y mapa de atributos) y **solo** trabaja sobre el directorio persistente de modelos `--data-root` con el editor detenido. Prepara todos los cambios antes de editar, conserva copias de seguridad checksumadas, escribe con reemplazos atómicos, conserva journal y permite recuperación/rollback sin perder modificaciones ajenas. Rechaza conflictos de nombres, metamodelos/versiones inesperados, symlinks y modificaciones del usuario posteriores a la migración. No instala ni actualiza ningún binario Tauri.

```json
{
  "schemaVersion":1,
  "metamodelId":"library",
  "fromVersion":"1.0",
  "toVersion":"1.1",
  "renameAttributes":{"attribute:class:Book:pages":"attribute:class:Book:pageCount"}
}
```

```powershell
python .\scripts\distribution\migrate_standalone_models.py migrate --data-root C:\proyectos\editor\user-data --plan C:\proyectos\editor\migration.json --editor-stopped
python .\scripts\distribution\migrate_standalone_models.py rollback --data-root C:\proyectos\editor\user-data --editor-stopped
```

Se ha ejecutado el test integrado `fix180_real_editor_migration_e2e.py`: el servidor Java **emitido de la plantilla de producción** crea dos modelos reales mediante HTTP; después se migra con el editor detenido, reinicia y consulta ambos modelos, y finalmente verifica un rollback byte a byte por HTTP. **No son actualizaciones nativas Tauri firmadas**.

## Calificación Windows, con un solo gate global heredado

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\validation\validate_fix180_megablock8_9.ps1 -StructuralOnly
powershell -ExecutionPolicy Bypass -File .\scripts\validation\validate_fix180_megablock8_9.ps1
```

El primer comando ejecuta las pruebas focales y las comprobaciones estructurales anteriores; el segundo añade el E2E real y finalmente llama **una vez** al calificador global heredado de Fix179 mediante Fix180 etapa 1. Maven completo, Vitest/Playwright, runners nativos y artefactos oficiales externos deben pasar antes de calificar un release.

## Criterios estrictos pendientes, aunque todas las pruebas locales pasen

1. **B8/B9:** EMF y Langium reales producidos por el propio exportador; dos suites nativas con outputs semánticos comparables; cardinalidades, enlaces, validaciones, Quick Fixes, eventos, gamificación y transformaciones cubiertos por evidencia fresca. No aceptar una capacidad solo declarada.
2. **D8:** MSI/AppImage/DMG instalados con apertura/edición/guardado/reinicio por SO; un simple arranque de proceso no es un ciclo de vida completo. macOS `.app` copiado no equivale a instalar un `.dmg`.
3. **D9:** integrar backup/migraciones verificadas con Tauri firmado y su actualizador oficial, con rollback tanto de binarios como de datos de modelo. No confundir Ed25519 del registro HYDRA con la firma propia de Tauri.

Los desarrollos de Archify siguen fuera del inventario modificado.
