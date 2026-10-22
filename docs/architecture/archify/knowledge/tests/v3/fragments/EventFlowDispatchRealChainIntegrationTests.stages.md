Etapas certificadas por EventFlowDispatchRealChainIntegrationTests:
- events_execute_flow.d0: theFirstEventAutoDeploysAnExplicitStop... (l.124-155): autodespliegue, stop(scope) bloquea el rearranque y start(scope) lo reactiva.
- events_execute_flow.a2: anEventRunsOnlyTheMatchingEnabledFlow (l.161-176): solo el flujo coincidente y habilitado produce traza.
- events_execute_flow.d1: aFalseStepConditionFilters... (l.183-212): traza FILTERED con testigo descartado y COMPLETED si la condicion es verdadera.
- events_execute_flow.a1: aWaitingStepIsResumedOnlyBy... (l.217-247) y anAbsenceWatchIsCancelledByItsEvent... (l.251-277): reanudacion por clave, cancelacion de vigilancia y tick sin emision.
