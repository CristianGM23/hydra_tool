Prueba -> etapa (QuickFixApplyUndoRedoRuntimeIntegrationTests)
- validation_apply_quickfix.list / x_validate_fix_revalidate_reward.pick: resolveWithoutPriorReportRunsFullValidationAndListsRealFixes, linea 218 (seis correcciones listadas).
- .pv: previewKeepsModelIntactAndApplyRevalidatesIncrementally, lineas 232-235 (XMI intacto tras previsualizar).
- .ap / .rv / .st: mismo metodo, lineas 240-245 (XMI cambiado, APPLIED_RESOLVED, revalidacion INCREMENTAL, delta resuelto); estados NO_CHANGES/APPLIED_UNRESOLVED en statusIsComputed..., lineas 353-359.
- .evq / x_..evt: lineas 247-251 (execution-completed, violation-resolved, quickfix started/applied).
- .undo: undoRedoRevalidate..., lineas 262-283 (UNDONE, REDONE, hasUndo/hasRedo, redo vaciado).
- .par: unknownOrMissingArguments..., lineas 296-303; .rb: falsePrecondition... (319) y failureAfterTheModelWasMutated... (339).
- .hist: statusIsComputed..., lineas 366-370 (NOT_SUPPORTED no guarda historial).
