# Hallazgos de producción surgidos al certificar (V3)

No se modificó código de producción. Estado: **confirmado por test** = un test PASS fija el comportamiento actual (fallará a
propósito si se corrige); **leído** = deducido de leer el código, sin test.

| # | Hallazgo | Estado | Test / lugar |
|---|---|---|---|
| 1 | El perfil `standalone-web` ejecuta `cmd /c` (Windows) o `sh`, que no están en `hydra.security.external-process.allowed-executables` (`application.properties` ~l.808); `HydraExternalProcessPolicy` los rechaza y GENERATE queda FAILED | confirmado por test | `GeneratedProjectLifecycleRealProcessIntegrationTests#productionStandaloneWebProfileIsRejectedByTheExternalProcessPolicy` |
| 2 | Persistencia de update-site con el fixture Sirius: `IllegalStateException` (fidelidad) sale como **500 INTERNAL_ERROR** en vez de 4xx, y la persistencia rechazada **no es atómica** (quedan el diagrama del metamodelo y un SVG) | confirmado por test | `UpdateSiteRecoveryHttpLifecycleIntegrationTests` |
| 3 | `UpdateSiteMetamodelReconciliationService.merge` usa el candidato primario para toda clave sin conflicto y falla («does not provide CLASS:Root») si un elemento solo lo aporta otro candidato | confirmado por test | idem |
| 4 | Con todas las capas, el preview de update-site no es aplicable por `DUPLICATE_NODE_MAPPING` (mismos mappings copiados en cada representación) | confirmado por test | idem |
| 5 | El adaptador Sirius falla con «id must be a stable document id» en la importación de proyecto externo: editores, reglas y quick fixes salen en 0 | confirmado por test | `ExternalProjectRecoveryEndToEndIntegrationTests` |
| 6 | `VisualValidationAssociationService.commit` crea `<id>:validations`, que `StableIdentifierPolicy` rechaza; lanza tras haber persistido el metamodelo, sin documento de validaciones | confirmado por test | `VisualImportMetamodelAssociationRealStackIntegrationTests` |
| 7 | `ActionModelApplicationService.create` / `ValidationModelApplicationService.create` guardan aunque el comprobador devuelva errores (los «rechazos» son diagnósticos del informe) | confirmado (comportamiento reflejado en tests) | tests de definición de reglas/quick fixes |
| 8 | `RoundTripManifestService.create` genera el manifiesto antes de que se añadan `META-INF/hydra-roundtrip.json`, `hydra-project-validation.json` y `verify-hydra.*`; un `/sync/preview` tras un apply limpio los marcaría `ORPHAN_NATIVE` | leído | — |
| 9 | Una edición manual solo nativa se clasifica `MANUAL_CONFLICT` («Both canonical and native versions changed») aunque el canónico no cambió | leído | — |
| 10 | `CapabilityLayerTransactionService` escribe con `Files.move` sin pasar por `CanonicalDocumentStore`: no hay línea de auditoría (`x_package_and_move_capabilities.r2` no se reclama) | leído | — |
| 11 | `CapabilitySemanticMergeService.collectIdentities` registra `quickFixId`, `validationId`, `actionId`, `ruleId`… como identidad y como referencia: una referencia colgante por esos campos nunca se detecta | leído | — |
| 12 | `TransformationIncrementalSynchronizationEngine.synchronize` añade la dirección aunque `propagate` no aplique nada (todo ignorado) → puede anunciar una dirección sin cambios | leído | — |
| 13 | `TransformationNativeParityService.execute` lanza `IllegalArgumentException` con `sourceModel` nulo, mientras `TransformationParityService.verify` devuelve `INVALID_EVIDENCE` | leído | — |
| 14 | `IllegalStateException` sin handler propio en `ApiExceptionHandler` → 500 (consulta/guardado de borrador roto) | leído | — |
| 15 | El Quick Fix INSERT al final del texto propone `valor` sin separador (pegaría `nodevalor`) | leído | — |
| 16 | Empaquetar un editor inválido, con secretos o con enlaces simbólicos lanza `IllegalStateException` → 500 en vez de 4xx | leído | — |
| 17 | `MetamodelValidationApplicationService.analyze` marca `generationReady` solo con `persisted=true` (coherente con un test existente) | informativo | — |

## Fase 2: correcciones (pendientes de ejecutar el test; sin compilar)

| # | Estado | Corrección / test de regresión |
|---|---|---|
| 10 | corregido | `CapabilityLayerTransactionService` notifica a los `CanonicalDocumentMutationObserver` (auditoría) al aplicar y al revertir; `CapabilityLayerTransactionAuditIntegrationTests` |
| 11 | corregido | `collectIdentities` ya no registra `quickFixId`/`validationId`/`actionId`… como identidad; `CapabilitySemanticMergeDanglingReferenceTests` |
| 12 | corregido | `Propagation.applied`: solo se anuncian las direcciones que propagaron; `TransformationIncrementalSynchronizationDirectionTests` |
| 13 | corregido | `TransformationNativeParityService.execute` devuelve `INVALID_EVIDENCE` (`$.sourceModel`); `TransformationNativeParityMissingSourceTests` |
| 14 | corregido localmente | `TextualDraftNotReadyException` + `@ExceptionHandler` solo en `TextualModelSessionController` (409 CONFLICT); handler global intacto; `TextualBrokenDraftRecoveryIntegrationTests`. Otros `IllegalStateException` siguen en 500 |
| 15 | corregido | La inserción al final del texto lleva separador (`" valor"`); `TextualBrokenDraftRecoveryIntegrationTests` |
| 16 | corregido | `StandaloneDistributionRejectedException` (subclase de `IllegalStateException`) → `IllegalArgumentException` → 400 en `packageEditor`/`packageReleaseReady`; política intacta; `StandaloneDistributionHttpFlowIntegrationTests` |

## Fase 2, ronda 2: defectos hallados por los E2E reales y corregidos (con regresión)

| # | Defecto | Corrección | Regresión |
|---|---|---|---|
| 18 | Una regla M2T sin condición se devolvía con `guard: null` y el diseñador se rompía («reading 'root'») | `@JsonInclude(NON_NULL)` en `TransformationGenerationRule` | `TransformationGenerationRuleJsonTests` |
| 19 | `EditorSessionEventPublisher` no copiaba `hydra.gamificationEnabled` a los metadatos: el editor no podía apagar la gamificación | copia el interruptor a los metadatos | `GamificationEventGateIntegrationTests` |
| 20 | Deshacer un quickfix no compensaba los puntos: `violation-resolved` no llevaba `diagnosticId` | el evento lleva `diagnosticId` | `ValidateFixRewardChainIntegrationTests`, `ValidationRuntimeEventFactoryTests` |
| 21 | `CapabilityPackageArchiveException` y la puerta de publicación no apta salían como 500 | 400 (archivo) y 409 (puerta) con handler local; E/S y deshabilitado siguen en 500 | `CapabilityPackageArchiveHttpLayerSelectionIntegrationTests`, `HydraFinalVerifiedPublicationRealGateIntegrationTests` |
| 22 | API de flujos de eventos: el frontend leía `.model` y el servidor envía `eventFlowModel`; además no normalizaba textos omitidos (`reading 'split'`) | `eventFlowNormalization.ts`, `toEventFlowLoadResult`, `flowPresentation.ts` tolerante | `flow/api.test.ts`, `flow/flowPresentation.test.ts`, E2E `flow-author` |
| 23 | Diagramas de gamificación/eventos/flujos: aristas nuevas sin `lineWidth` y demás numéricos → 400 («Cannot map null into type double») | `withHydraConnectionDefaults` al guardar | `gamification-designer/api.test.ts`, E2E de gamificación |
| 24 | Vista de ledger leía `achievementIds`/`rewardIds` y el servidor envía `achievements`/`rewards` | la vista lee los campos reales con valores por defecto | `GamificationLedgerView.test.tsx` |
| 25 | Respuesta de crear/reemplazar catálogo de gamificación sin normalizar: `conditionPath.trim()` sobre `undefined` | normalización igual que en la carga | `gamification-designer/api.test.ts` |
| 26 | `TextualModelSession.draftValid()` no se serializaba: «Promote valid model» nunca se habilitaba | `@JsonProperty(READ_ONLY)` en el método | `TextualModelSessionDraftValidJsonTests`, E2E textual |
| 27 | Perfil de integración: el servidor omite colecciones y textos vacíos y la interfaz fallaba al guardar/recargar («collections are incomplete», `reading 'trim'`) | `normalizeIntegrationProfile` completa las colecciones y textos omitidos | `integrationProfileNormalization.test.ts`, E2E de integración |
| 28 | Editor de definiciones: el backend devuelve `semanticEdge: null` y la normalización solo trataba `undefined` («reading 'defaultValues'») | `== null` en mapeos y herramientas de arista | `editorDefinitionNormalization.test.ts`, E2E de editores |
| 29 | Diálogo de generación de código: dependencias inestables del efecto reiniciaban la carga de destinos más de 25 veces y el selector quedaba vacío | las funciones y objetos inestables se leen por referencia | `ModelCodeGenerationDialog.test.tsx`, E2E de generación de código |

## Continuación (fase 3 de la certificación)

| # | Hallazgo / corrección | Estado | Test / lugar |
|---|---|---|---|
| 1 | `standalone-web` ya no usa `cmd`/`sh`: el perfil de producción ejecuta `javac` y `java` (permitidos) y el servidor generado tiene un modo `--check`; la lista de ejecutables y la política no se tocaron | **corregido** (fase 3) | `GeneratedStandaloneWebProfileRealLifecycleIntegrationTests`, `GeneratedProjectLifecycleRealProcessIntegrationTests` |
| 5, 6 | los ids de documento ya no llevan `:` (`<id>.validations`); el test de recuperación externa lleva una regresión | corregidos antes de esta fase (la tabla superior no se actualizó) | `VisualImportMetamodelAssociationHttpIntegrationTests`, `ExternalProjectRecoveryEndToEndIntegrationTests` |
| 30 | `ProjectExplorerWorkspace.openSelectedEntries` abre varios ficheros en bucle con `openFile`, que guarda las pestañas a partir de `tabs` del render actual: con selección múltiple `restoredTabs` solo conservaría el último | **corregido** (fase 3: las pestañas se leen del último render con `tabsRef`) | `ProjectExplorerWorkspace.tsx`; regresión E2E «Open sobre una selección múltiple» en `tree-browse-open.spec.ts` |
| 31 | `GrammarCompilationService` lanza el fallo de análisis bloqueante fuera de la medición de etapa: `FULL_ANALYSIS` queda SUCCESS y se atribuye el fallo a `RESOLVE_IMPORTS` | leído | `GrammarCompilationService.java` l.92, 179-180 |
| 32 | La descripción de `x_grammar_to_text_model.an` dice que solo bloquea la recursión izquierda; también bloquean regla de entrada ausente, referencias sin resolver y símbolos duplicados | discrepancia de descripción | `GrammarSourceAnalyzer.java` l.68, 130 |
| 33 | Ni guardar ni compilar una gramática enlazada comprueba que el metamodelo exista (solo que no esté vacío) | leído | `GrammarApplicationService.java` l.57-58, `GrammarCompilationService.java` l.109 |
| 34 | Las capas de las que depende otra no se añaden solas; una dependencia obligatoria no elegida hace fallar el export con `DEPENDENCY_TARGET_MISSING` | discrepancia de descripción | `CapabilityPackageDependencyResolver.java` l.20-34, `CapabilityPackageChecker.java` l.66-68 |
| 35 | La decisión por conflicto no es obligatoria (MERGE por defecto) aunque el proceso dice que ninguna fusión es implícita | discrepancia de descripción | `CapabilitySemanticMergeRequest.java` l.19 |
| 36 | La recuperación de proyecto externo no tiene vista previa aplicable ni confirmación explícita (`/analyze` y `/import` directo); si algo falla tras `materialize`, los documentos ya guardados no se revierten (solo se borra el proyecto) | leído | `ExternalProjectImportService.java` l.65-74 |
| 37 | El perfil de integración hacia la tecnología de origen nunca se crea; solo existen sugerencias en el informe | leído | `ExternalProjectRecoveryService.java` l.289-309 |
| 38 | `ValidationModelApplicationService.create` guarda aunque el comprobador devuelva errores (ya registrado como #7), y la descripción de `gamification_award.gate` corresponde al filtro del bus, no a `process` | discrepancia | `GamificationRuntimeService.java` l.83-85, 98 |
| 39 | La descripción de `validation_apply_quickfix.list` dice «solo activas y con precondición cumplida»; `QuickFixRuntimeService.resolve()` solo comprueba que la acción exista | discrepancia de descripción | auditoría semántica de la fase 1 |
| 40 | `x_generate_certify_distribute.tgt` dice que no hay entrada desde una sesión de texto, pero `TextualModelSessionCodeGenerationController` genera desde una sesión de texto | discrepancia de descripción | `TextualModelSessionCodeGenerationController.java` l.28-71 |
| 41 | Los activos SVG por contenido no se borran en el rechazo/deshacer de update-site (`cleanupImportedLayers`) | leído | `UpdateSiteLayerMaterializationService.java` l.169-195, `EditorVisualAssetService.java` l.31 |
| 42 | `DesignerDropdown`: su `onClick` recibe los clics del diálogo que se muestra en un portal (los eventos de React burbujean por el portal) y cierra el desplegable que lo contiene; solo se libra lo que lleva `data-hydra-menu-keep-open`. «Elegir .odesign» y «Analizar» del diálogo de importación visual podrían cerrar el diálogo en uso real | **corregido** (fase 3: los eventos que llegan por el portal ya no cierran el desplegable) | `DesignerDropdown.tsx`; regresión Vitest en `DesignerDropdown.test.tsx` |
| 43 | El menú contextual del explorador de proyectos limita su posición suponiendo 304 px de alto, pero tiene 12 órdenes (~450 px): con una fila baja o ventana pequeña las últimas órdenes quedan fuera de pantalla y, al desplazarse, el menú se cierra | **corregido** (fase 3: reserva 460 px en lugar de 304) | `ProjectExplorerWorkspace.tsx` l.264-265 |
| 44 | `RemoteGitWorkspaceService.branchListingCommand` usaba `%x1f` como separador en `git for-each-ref` (la forma `%xNN` es de `git log`; en `for-each-ref` es `%1f`): el árbol del repositorio remoto salía siempre sin ramas | **corregido** (fase 3) | `RemoteGitFetchPullPushLocalRemoteIntegrationTests` |
| 45 | Los contadores `ahead`/`behind` del árbol no se leían cuando eran lo primero tras `[` (`[behind 1]`, `[ahead 1]`): la expresión solo admitía separador, coma o espacio delante | **corregido** (fase 3) | `RemoteGitFetchPullPushLocalRemoteIntegrationTests` |

Hallazgos sin corregir (documentados): restos del #14 (otros `IllegalStateException` en 500 sin tocar el handler
global), `hydra.eventFlowsEnabled` sigue en el payload del evento del editor, SVG huérfano tras una persistencia de
update-site rechazada, la barra del diseñador de editores tapa los «+» del árbol con el tamaño de ventana por defecto
(posible defecto de interfaz sin confirmar visualmente), el asistente guiado de integración pierde las confirmaciones al
cambiar de vista, y el arrastre entre puertos del lienzo de metamodelos no crea la relación en el navegador automatizado.