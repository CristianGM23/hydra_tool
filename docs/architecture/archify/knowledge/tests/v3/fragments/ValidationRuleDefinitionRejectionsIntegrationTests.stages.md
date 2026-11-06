Prueba -> etapa (ValidationRuleDefinitionRejectionsIntegrationTests)
- validation_define_rule.ctx: rejectsContextsThatDoNotMatchTheRealMetamodel, lineas 74, 80, 86 (CONTEXT_CLASS_REQUIRED, CONTEXT_FEATURE_OWNER_MISMATCH x2).
- .trg: rejectsTriggersThatContradictTheExecutionMode, lineas 94-100 (MANUAL/FULL mismatch, TRIGGER_REQUIRED).
- .cnd: rejectsConditionsThatAreMissingOrNotBoolean, lineas 108 y 111.
- .qf: rejectsDuplicatedOrUnknownQuickFixes, lineas 119 y 122.
- .sv: refusesToSaveValidationsBefore..., lineas 131-138 (lanza y no deja fichero) y savesAValidRule..., linea 147 y siguientes (JSON en disco).
- .chk: todos los codigos anteriores salen del informe del comprobador real. Etapa msg descartada: ninguna asercion comprueba el mensaje.
