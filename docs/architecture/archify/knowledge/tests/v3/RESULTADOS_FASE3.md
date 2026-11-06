# Resultados de la continuación V3 (fases 1–6) — documento vivo

Estado de partida (línea base documentada): 980 etapas, 827 STRONG, 88 MEDIUM, 24 LIMITED, 41 sin mapear.
Instantánea a nivel de etapa de esa línea: `target/archify-v3/snapshots/phase3-baseline-827.json`.

## Fase 1 — vigencia y suficiencia

### 1.1 Vigencia de la evidencia histórica
- `v3/audit-currency.js`: cierre transitivo de dependencias de cada test Java comparado con el inicio de la evidencia retenida
  (los tests Spring dependen de todo `src/main`). Resultado inicial: 29 tests invalidados, 89 vigentes, 16 dependientes de Spring.
- Se reejecutaron únicamente los 29 invalidados más 2 tareas nuevas de línea base
  (`v3.regression-local-directory-project-storage-tests`, `v3.regression-project-index-and-change-tests`): 31/31 PASS.
- Segunda pasada de `audit-currency.js`: 0 invalidados, 0 etapas con evidencia solo histórica e invalidada.
- Los ficheros restaurados con `git checkout` tienen mtime posterior; eso causó parte de las invalidaciones.

### 1.2 Auditoría semántica de los 30 tests que acreditan más etapas (397 pares test–etapa)
Cinco auditorías independientes de solo lectura (`target/archify-v3/phase3/claims/audit-1..5.json`), rúbrica en
`target/archify-v3/analysis/BRIEF_AUDITORIA_SEMANTICA.md`. Veredictos: 309 SUPPORTED, 73 WEAK, 15 UNSUPPORTED.
- 59 WEAK → `to_indirect` (la etapa queda sin prueba directa), 15 UNSUPPORTED → `remove_direct`, 14 WEAK se mantienen (la parte
  principal sí está probada; la limitación queda en el texto del fragmento).
- Aplicado con `target/archify-v3/tools/apply-audit.js` sobre los fragmentos (8 tests preexistentes sin fragmento V3 recibieron un
  fragmento `remap-audit-*.json` sembrado desde su registro de catálogo; `ModelCodeGenerationServiceTests` recibió tarea y reejecución).
- Resultado: STRONG 827 → 755 (−72). No se baja por número de pruebas, solo por aserciones que no demuestran la etapa.
- Hallazgos de contenido (descripción del proceso vs. comportamiento real): "Listar correcciones" (`validation_apply_quickfix.list`) dice
  "solo activas y con precondición cumplida" pero `QuickFixRuntimeService.resolve()` solo comprueba que la acción exista.

## Fase 3 — defectos de producción

### Trazabilidad (`tree-snapshot.js --diff target/archify-v3/snapshots/tree-phase3-start.json`)
Cambios de producción hechos en esta continuación (el resto de ficheros modificados en el árbol son anteriores y no son de esta fase):
| Fichero | Cambio |
|---|---|
| `src/main/java/.../generated/project/GeneratedProjectRuntimeProfileCatalog.java` | el perfil `standalone-web` deja de ejecutar `cmd /c` / `sh` y usa solo `javac` y `java` (ya permitidos): construir = `javac --release 21 -d backend/out backend/src/HydraStandaloneServer.java`, verificar = `java -cp backend/out HydraStandaloneServer --check`, lanzar = `java -cp backend/out HydraStandaloneServer` |
| `src/main/resources/standalone-template/backend/src/HydraStandaloneServer.java.template` | nuevo modo `--check`: comprueba `standalone-editor.json` y `frontend/index.html`, inicializa el almacenamiento y sale con 0 o 1 sin escuchar |

Defecto #1 (antes sin corregir): corregido **sin** ampliar `hydra.security.external-process.allowed-executables` ni tocar la política.
Regresión: `GeneratedStandaloneWebProfileRealLifecycleIntegrationTests` (generador real → perfil de producción → `javac`/`java` reales; caso negativo
sin `frontend/index.html` = código de salida 1) y `GeneratedProjectLifecycleRealProcessIntegrationTests#productionStandaloneWebCommandsPassTheDefaultExternalProcessPolicy`
(sustituye al test que caracterizaba el rechazo). Reejecutadas 11 tareas relacionadas: 11/11 PASS. Los proyectos generados antes del cambio no
traen el modo `--check` y deben regenerarse. El spec E2E `generated-project-wizard` quedó STALE por este cambio y se reejecutó: PASS.

Estado de los defectos #5/#6 de `HALLAZGOS_PRODUCCION.md` (id de documento no estable): la tabla los daba por «confirmados por test», pero el código actual ya los trata
(`VisualValidationAssociationService` usa `<id>.validations`; el test de recuperación externa lleva una regresión «no contiene stable document id»), con arreglo anterior a
esta fase. El defecto #2 (500 en update-site con Sirius): una lectura del código actual indica que ya responde 400 (`UpdateSiteLayerMaterializationService` lanza `IllegalArgumentException`); queda sin corregir el borrado de los activos SVG tras un rechazo (#41). El test de recuperación de update-site (`UpdateSiteRecoveryHttpLifecycleIntegrationTests`) acepta 200 o 400, así que no fija cuál.

## Fase 4 — evidencia obsoleta y FAIL histórico (`target/archify-v3/tools/stale-fail.js`)
Estados del almacén de ejecución: PASS 879, DISCOVERED 129, SKIPPED 2, STALE_EVIDENCE 49, FAIL 1.
- STALE_EVIDENCE: 48 son pasos de puertas de release (`release`, `rc1`, `rc2`: evidencia «carry-forward» con ~3 482 ficheros cambiados desde entonces) y 1 era el spec E2E
  `generated-project-wizard` (cambió el catálogo por mi corrección; reejecutado, ya PASS). Las 48 son requisitos de **publicación**, no de certificación funcional: no se
  reejecuta la cualificación completa (requiere la matriz de sistemas operativos y firma). No se editan ni se simulan.
- FAIL histórico: `readiness:51:backend-spanish-method-documentation` (11/10: 10 024/10 029 métodos documentados). Reejecutado hoy el verificador de solo lectura
  `scripts/validation/verify_backend_spanish_method_docs.py`: `PASS documented=10043/10043`. El registro histórico queda como está.
- 14 registros STRONG sin PASS propio: 2 suites omitidas por requisito externo (`NativeGeneratedProjectAcceptanceTests`, `LiveRemoteRepositoryRoundTripTests`), 8 scripts
  `scripts/validation/*` nunca ejecutados por V3 (DISCOVERED) y 4 puertas de release obsoletas. Ninguna etapa STRONG depende solo de ellos (`audit-strong`); se conservan sin tocar.

## Fase 2 — etapas pendientes
Requisitos externos y cómo certificarlas después: `REQUISITOS_EXTERNOS.md`. Lista etapa a etapa: `ETAPAS_PENDIENTES_ANALISIS_FASE3.csv` (regenerar con `target/archify-v3/tools/pending153.js`).
Trabajo hecho (agentes de solo escritura + ejecución propia en una sola invocación del runner):
- 13 clases Java nuevas (B1 4, B2 2, B3 5, B4 2) y 1 de regresión del perfil `standalone-web`: 14 en total, todas PASS.
- 1 spec Playwright nuevo (`transformation/toolchain-provisioning-ui`, 3 tests) y tests nuevos añadidos al final de 14 specs existentes; 1 test E2E de regresión
  (`tree-browse-open`) y 1 test Vitest nuevo (`DesignerDropdown.test.tsx`).
- 3 tareas de solo regresión (2 de línea base de proyecto y 1 de `ModelCodeGenerationServiceTests`) para fijar vigencia tras los remapeos.
- Dos etapas reclamadas por los agentes se rebajaron antes de fusionar por la rúbrica: `gamification_award.per` (el fallo del historial es simulado) pasó a indirecta y
  `validation_author_form.ex` (el recorrido de 13 tipos de nodo no llegó a guardar en la ejecución real; queda como `test.fixme`).
- Decisiones honestas sin reclamar: etapas solo de interfaz o de decisión humana, discrepancias de descripción (ver `HALLAZGOS_PRODUCCION.md` #30-#45) y externas.

## Defectos de producción corregidos en esta continuación (con regresión)
| # | Defecto | Regresión |
|---|---|---|
| 1 | `standalone-web` rechazado por la política de procesos (ahora `javac`/`java`) | `GeneratedStandaloneWebProfileRealLifecycleIntegrationTests` |
| 30 | pestañas abiertas obsoletas al abrir varios ficheros | E2E `tree-browse-open` («Open» sobre selección múltiple) |
| 42 | los clics de un diálogo abierto desde un desplegable lo cerraban | `DesignerDropdown.test.tsx` |
| 43 | menú contextual del explorador fuera de pantalla | (cubierto por `tree-browse-open`/`export-selection-zip`) |
| 44 | árbol de ramas de Git siempre vacío (`%x1f` en `for-each-ref`) | `RemoteGitFetchPullPushLocalRemoteIntegrationTests` |
| 45 | `ahead`/`behind` no se leían tras `[` | `RemoteGitFetchPullPushLocalRemoteIntegrationTests` |
Cambios de producción de esta fase: `GeneratedProjectRuntimeProfileCatalog.java`, `HydraStandaloneServer.java.template`, `RemoteGitWorkspaceService.java`,
`frontend/src/designer-ui/DesignerDropdown.tsx`, `frontend/src/project-explorer/ProjectExplorerWorkspace.tsx`. No se tocaron la lista de ejecutables permitidos ni la política de procesos.

## Resultado final (entregables)
Matriz oficial actual (`category-matrix.js`; tareas del manifiesto: 274, todas PASS; 0 tests invalidados):

| Categoría | Etapas | STRONG | MEDIUM | LIMITED | Sin test |
|---|---|---|---|---|---|
| audit | 4 | 4 | 0 | 0 | 0 |
| cross | 174 | 130 | 17 | 12 | 15 |
| events | 89 | 80 | 7 | 1 | 1 |
| external_repositories | 36 | 17 | 17 | 1 | 1 |
| gamification | 66 | 65 | 1 | 0 | 0 |
| generated_projects | 15 | 4 | 11 | 0 | 0 |
| grammar | 100 | 87 | 7 | 4 | 2 |
| integration | 40 | 35 | 1 | 3 | 1 |
| metamodel | 52 | 44 | 3 | 2 | 3 |
| model_editor | 57 | 42 | 2 | 7 | 6 |
| persistence | 6 | 6 | 0 | 0 | 0 |
| portability | 23 | 21 | 0 | 1 | 1 |
| project | 42 | 35 | 4 | 0 | 3 |
| publication | 43 | 37 | 5 | 1 | 0 |
| recovery | 36 | 26 | 8 | 2 | 0 |
| security | 7 | 7 | 0 | 0 | 0 |
| toolchains | 23 | 19 | 3 | 0 | 1 |
| transformation | 88 | 87 | 0 | 0 | 1 |
| validation | 79 | 70 | 4 | 3 | 2 |
| **Total** | **980** | **816** | **90** | **37** | **37** |

Comparación con la línea base documentada (827 STRONG, 88 MEDIUM, 24 LIMITED, 41 sin mapear): STRONG −11, MEDIUM +2, LIMITED +13, sin mapear −4.
Trayectoria: 827 → 755 tras la auditoría semántica (−72 etapas cuyas aserciones no demostraban el resultado) → 816 tras las pruebas nuevas.
Cambios etapa a etapa: `ETAPAS_CAMBIOS_FASE3.csv` (79 etapas: 37 suben, 42 bajan). 121 procesos: `PROCESOS_121_FASE3.csv` (56 totalmente STRONG; la línea base tenía 58).
Instantáneas: `target/archify-v3/snapshots/phase3-baseline-827.json`, `phase3-after-audit.json`, `phase3-final.json`.
Las 164 etapas no STRONG se reparten así (ver el CSV de pendientes): externas (OAuth/proveedores/toolchains nativas), solo interfaz o decisión humana, discrepancias de descripción
documentadas, y 11 de `generated_projects` (los perfiles Langium, Xtext, GLSP y BESSER necesitan herramientas externas, y el lanzamiento del servidor generado solo se planifica).

### Tests nuevos, reutilizados e invalidados
- Nuevos: 14 clases Java, 1 spec Playwright, tests añadidos en 14 specs, 1 Vitest, 3 tareas de regresión.
- Invalidados y reejecutados: 29 tests Java por cambios de producción posteriores a su evidencia (+2 tareas de línea base), 17 tests Spring tras la corrección de Git,
  y los 55-67 specs Playwright cuya huella incluye todo `src/main` y `frontend/src` (reejecutados en dos ocasiones tras cambiar producción).
- Reutilizados: los 274 del manifiesto quedan REUSE con huella vigente (ninguno se repite si no cambia algo).

### Informe de vigencia de la evidencia histórica
`audit-currency.js` (cierre transitivo de dependencias; tests Spring dependen de todo `src/main`): 132 tests Java, 132 VALID, 0 INVALIDATED, 0 etapas STRONG sin evidencia
válida, 0 etapas solo históricas e invalidadas. Evidencia obsoleta de publicación (48 pasos carry-forward) y el FAIL histórico: ver Fase 4; no se editaron.
Aviso: la herramienta compara por fecha de modificación; los ficheros restaurados con `git checkout` cuentan como cambiados (conservador).

### Limitaciones reales y requisitos externos pendientes
`REQUISITOS_EXTERNOS.md` (proveedores remotos con OAuth y repositorios reales, Maven con red y toolchains nativas, cualificación de publicación). Etapas de interfaz sin
test directo posible: el árbol de accesibilidad real y el navegador no ejercen todos los botones; se anotan en `limits` de cada fragmento. Se observó un caso de flakiness
en Playwright (`grammar/textual-model-edit-fix-save` falló una vez dentro del conjunto y pasó aislado).

### Comando único para reanudar
`python scripts/archify/v3/hydra_archify_v3.py run --retry-failed --include-stale --timeout 7200`
Último punto de control: manifiesto de 274 tareas, 284 veredictos PASS, `run-phase3-currency2.log` (17 tareas reejecutadas), todo REUSE. El estado inmutable está en
`target/archify-v3/` (git-ignorado; conservarlo). No se hicieron commits ni pushes. La documentación de `Downloads/HYDRA_PARALELO_ANALISIS_RESUMIBLE` no se tocó.
