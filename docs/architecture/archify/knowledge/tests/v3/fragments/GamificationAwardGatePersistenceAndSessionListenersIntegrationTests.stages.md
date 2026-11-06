GamificationAwardGatePersistenceAndSessionListenersIntegrationTests - stage mapping
- gamification_award.gate: theGateIgnoresSwitchedOffNoMetamodelAndTooDeeplyChainedEvents (l.154-173): system switch (property), session switch (metadata), blank metamodel and depth 16 (bus and direct call) leave no ledger; depth 15 scores 2 points.
- gamification_award.per: theStateIsSavedBeforeTheHistoryAndRolledBackWhenTheHistoryFails (l.211-227) records the save order and the state restore from disk; aLedgerFailureOnTheFirstEventRemovesTheJustSavedState (l.242-244) shows state deletion.
- x_event_flow_to_reward.idem: aRepeatedEventDoesNotGrantTwiceNorRewriteTheDocuments (l.192-199): empty result and byte-identical state and ledger files.
- gamification_author_form.art: aSavedCatalogIsUsedByAFreshEngineToGrantPointsAndAnAchievement (l.256-273): catalogue JSON on disk and a new engine grants points and the achievement.
- events_publish_session.a6/a7: aSessionPublishedEventRunsTheFlowAndTheGamificationAwardsAndRetiresPoints (l.307-329): real session publisher runs the flow, ledger shows +5 and -2 and state 3; unrelated event and switched-off gamification add no points.
