Prueba -> etapa (QuickFixDefinitionPersistenceAndCheckIntegrationTests)
- validation_define_quickfix.id / .pol / .pre / .par / .op: definesPersistsAndReopens..., lineas 99-108 (GET), 114 (JSON de disco) y 121-128 (repositorio reabierto).
- .chk: checkerRejectsMissingOrNonBooleanPreconditions..., lineas 135-138 (via expectCode, linea 191) y actionWithoutOperations..., linea 146.
- .lnk: ruleLinkedToTheActionIsAccepted..., lineas 170 (enlace duplicado rechazado), 175-178 (regla valida releida con su quickFixId).
- Descartado: verificacion con QuickFixRuntimeService.resolve (exige modelo vivo, cubierto en QuickFixApplyUndoRedoRuntimeIntegrationTests) y las diez clases de operacion (se usan cinco).
