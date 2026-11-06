Etapas certificadas por EventFlowAuthoringPersistenceIntegrationTests:
- events_compose_flow.a1/a2/a5/a6/a8: aValidFlowWithTriggers... (l.146-195): disparadores, nodo START, reintento/plazo/compensacion, variable y fichero guardado con ETag = SHA-256.
- events_compose_flow.a4/a7 y events_author_flow.a4/a6: structurallyInvalidFlows... (l.200-260): checkDraft devuelve codigos exactos (FLOW_START_COUNT, DECISION_BRANCHES, JOIN_INPUT_COUNT, ORPHAN_NODE...), create/replace INVALID_REQUEST y fichero intacto.
- events_compose_flow.a3: oneNodeOfEveryOneOfTheTwentyNineKinds... (l.264-312): 29 clases en disco y compile() de 28 flujos.
- events_compose_flow.a8 (posiciones de cajas): boxPositionsAreSavedRereadAndGuardedByTheEtag (l.317-356) y recarga/compilacion/arranque en l.361-404.
