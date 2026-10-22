ValidateFixRewardChainIntegrationTests - stage mapping (STRONG)
- x_validate_fix_revalidate_reward.per (saved model, state and history): fixingTheModelAwardsPointsShowsTheNoticeAndPersistsEverything (l.264-278) reads the XMI, the ledger JSON (l.267) and the state JSON from disk.
- x_validate_fix_revalidate_reward.fb (reward or reversal notice): same test (l.282-285) for the reward notice; undoingTheFixCompensatesThePointsBecauseTheResolvedEventCarriesTheDiagnosticId (l.301-316) and anExplicitReferenceToTheAwardedEventRevertsThePointsAndShowsTheReversalNotice (l.340) for the reversal notice.
- Fixed: the resolved-problem event now carries diagnosticId, so the compensation matcher finds the award when the fix is undone.
