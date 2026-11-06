# Verificación previa a la fase 2 (2026-10-19)

## 1. Los dos ficheros de producción borrados y restaurados

**Qué pasó.** Un subagente (clúster portabilidad/repos) definió una función `rd` en PowerShell; `rd` es alias de
`Remove-Item` y gana a las funciones, así que `rd <fichero>` borró en vez de leer. Según el transcript del subagente
(`subagents/agent-ae0cab4a299ff102b.jsonl`), horas UTC:

| Hora (UTC) | Hecho |
|---|---|
| 14:56:35 | borra `src/main/java/com/example/editor/remote/storage/RemoteStorageProfileManager.java` |
| 14:58:10 | borra `src/main/java/com/example/editor/project/storage/LocalDirectoryProjectStorage.java` |
| 14:58:21 | `git checkout -- <ambos>`; `git status` deja de mostrarlos como borrados |

No se tocó ningún otro fichero con `git checkout`; no se restauró ni sobrescribió nada más en esta verificación.

**Evidencia de que el contenido restaurado es el previo** (SHA-256):

| Fichero | Restaurado hoy | HEAD (`1c800b0`) | Copia 05/10 (`target/release/work/rc2-extracted`) | `MANIFEST_SHA256.txt` del árbol de trabajo (13/10 22:17) |
|---|---|---|---|---|
| `LocalDirectoryProjectStorage.java` | `e968762c…0e7a` | idéntico | idéntico | idéntico |
| `RemoteStorageProfileManager.java` | `444070f0…1b4b` | idéntico | idéntico | idéntico |

Otros indicios coherentes: los demás ficheros de esos directorios conservan la marca uniforme `10/10/2026 12:00:00`
(los dos restaurados tienen hoy `19/10 16:58:32`, la del checkout); la referencia de la caché de Archify
`LocalDirectoryProjectStorage.java:310` sigue apuntando al método `configure`; `git stash list` está vacío y los 13 blobs
colgantes de `git fsck` no contienen ninguna de las dos clases (nunca hubo una versión modificada y añadida al índice);
VS Code no guarda historial local de estos ficheros (290 directorios revisados, ninguna entrada); la verificación de
documentación de métodos sigue en `PASS 10029/10029`.

**Incertidumbre que permanece (no se puede cerrar con los datos disponibles).** Lo único demostrable es que antes del
borrado el contenido era el de HEAD **como mínimo hasta el 13/10 22:17** (manifiesto de hashes local). No existe
ningún snapshot, checkpoint ni copia con fecha entre el 13/10 y el 19/10 14:56 UTC, y el `git status` inicial de la sesión
llegó truncado antes de la sección `src/`. Por tanto **no puedo demostrar** que no hubiera ediciones locales sin
commitear en esos dos ficheros durante esa ventana. Indicios en contra de que existieran: la marca de tiempo uniforme de
sus vecinos, el `git status` posterior (que muestra solo los cambios ajenos ya conocidos en `src/main`) y que ningún
agente ni sesión de esta campaña tuvo motivo para editarlos. Si el propietario sabe de un cambio local en esos
ficheros, debe tratarse como perdido y reponerse desde su copia.

## 2. Auditoría de la cuarentena de compilación del runner

Nuevo subcomando de solo lectura: `python scripts/archify/v3/hydra_archify_v3.py audit-evidence`
(salida `target/archify-v3/audit-evidence.json`, 16 pruebas unitarias del runner).

* **Cuarentena = estado no superado.** Una tarea cuyo test no compila recibe veredicto `COMPILE_ERROR` (nunca PASS).
  `classify` la deja en `SKIP` mientras sus entradas no cambian y en `RUN` en cuanto se corrige
  (`test_compile_error_is_never_reused_and_resumes_after_the_fix`); `run` devuelve código ≠ 0.
* **Sin cobertura STRONG.** `fragment_gate` retiene los fragmentos de toda tarea no PASS, así que el catálogo no recibe
  ningún registro ni etapa. Hecho real: durante la primera ejecución las 3 clases en cuarentena figuraban como
  `PENDING` (17 registros pendientes en `build --check`) y no contaban en la matriz.
* **Reanudable.** Historial real: 4 tareas pasaron por `COMPILE_ERROR` (3 en `run-20261019T094220Z`, 1 en
  `run-20261019T153218Z`), se corrigió el test y las 4 acabaron en PASS. Las 2 entradas de cuarentena están
  `restored=true`, sus ficheros presentes en el árbol y sus copias conservadas.
* **Correspondencia 90 PASS ↔ 55 clases nuevas ↔ Surefire.** Manifiesto: 90 tareas = 35 importadas de V2 + 55 generadas
  por fragmento. Los 90 veredictos son PASS. Para cada una: XML presente, SHA-256 igual al registrado, 0 casos fallidos,
  el índice apunta a la misma ejecución que el veredicto, el fichero de test no cambió después de iniciarse la evidencia
  y el estado de ejecución oficial de Archify es PASS. 55 de 55 clases V3 tienen XML inmutable. `problems: 0`.
* **Hallazgo corregido durante la auditoría.** 2 clases PASS contenían un caso omitido (SKIPPED) por no poder crear
  enlaces simbólicos en este host. Esa parte se separó en un caso aparte; las aserciones restantes pasan y la parte
  omitida queda declarada en `limits` y como aviso en `audit-evidence`, no como cobertura.

## 3. 970 registros de `build --check` frente a 980 etapas

Son unidades distintas: el catálogo cuenta **pruebas** (472 java, 261 vitest, 95 scripts, 121 puertas, 18 familias,
3 playwright = 970) y la matriz cuenta **etapas funcionales** del modelo (980). No son comparables 1 a 1: una prueba
puede cubrir varias etapas (489 registros mapean etapas, 481 no mapean ninguna) y una etapa puede tener varias pruebas.

Verificado con `node docs/architecture/archify/knowledge/tests/v3/verify-universe.js` (solo lectura):
121 procesos, 1245 nodos de los que 265 son de inicio/fin (121 + 144) y no son etapas → **980 etapas**; la matriz oficial
contiene **980** entradas, 0 etapas del modelo sin entrada, 0 entradas sin etapa, 0 procesos sin matriz, 0 etapas sin
estado de fuerza. 773 etapas están mapeadas por algún registro (736 de forma directa); las 207 restantes figuran sin
prueba, no fuera de la evaluación (la matriz cuenta 208 sin fuerza: la adicional es una etapa mapeada solo por un test omitido). Los 970 registros de catálogo y de estado de ejecución coinciden 1 a 1.
