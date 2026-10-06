import type { GamificationBindingModel, GamificationDefinitionModel, GamificationRuntimeSnapshot } from './types';
interface Props { definitions: GamificationDefinitionModel; bindings: GamificationBindingModel; runtime: GamificationRuntimeSnapshot | null; }
export function GamificationDiagram({ definitions, bindings, runtime }: Props) {
    return <div className="gamification-diagram">
        <section><h3>Verified evidence</h3>{bindings.rules.map(rule => <article className={rule.eventTypeId === 'core.quickfix.applied' ? 'invalid' : ''} key={rule.id}><strong>{rule.eventTypeId}</strong><small>{rule.name}</small><span>{rule.repeatPolicy}{rule.cooldownSeconds > 0 ? ` · ${rule.cooldownSeconds}s cooldown` : ''}</span></article>)}</section>
        <div className="gamification-arrows">→</div>
        <section><h3>Rules and progression</h3>{bindings.rules.map(rule => <article key={rule.id}><strong>{rule.effectKind === 'PENALTY' || rule.points < 0 ? 'Penalty' : 'Award'} · {Math.abs(rule.points)} {rule.pointsTypeId || 'points'}</strong><small>{rule.achievementId || rule.challengeId || rule.rewardId || 'progress only'}</small></article>)}{definitions.levels.map(level => <article className="level" key={level.id}><strong>{level.name}</strong><small>{level.minimumPoints} {level.pointTypeId}</small></article>)}</section>
        <div className="gamification-arrows">→</div>
        <section><h3>Rewards and ledger</h3>{definitions.rewards.map(reward => <article className="reward" key={reward.id}><strong>{reward.name}</strong><small>{reward.id}</small></article>)}<article className="ledger"><strong>{runtime?.ledger.entries.length ?? 0} ledger entries</strong><small>{Object.keys(runtime?.state.actors ?? {}).length} actor(s)</small></article></section>
    </div>;
}
