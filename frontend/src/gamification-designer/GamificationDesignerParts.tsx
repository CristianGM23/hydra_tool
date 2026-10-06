import { HydraButton } from '../design-system/HydraButton';
import { useHydraLanguage } from '../i18n';
import type { Edge, Node } from '@xyflow/react';
import { HydraAdaptiveSelect } from '../components/HydraAdaptiveSelect';
import { DesignerGuideButton, DesignerHelpedCheckbox, DesignerHelpedField, DesignerRequiredFieldsLegend } from '../designer-ui/DesignerFieldHelp';
import { HydraConnectionVisualEditor } from '../designer-ui/HydraConnectionVisualEditor';
import type { HydraEditableConnectionVisual } from '../designer-ui/HydraConnectionVisualEditor';
import { eventTypeOptionHelp, presentEventType } from '../event-designer/eventTypePresentation';
import type { EventTypeDefinition } from '../event-designer/types';
import type { GamificationEdgeData, GamificationNodeData } from './gamificationDiagram';
import type { AchievementDefinition, ChallengeDefinition, GamificationDefinitionModel, GamificationRule, GamificationRuntimeSnapshot, LevelDefinition, PointTypeDefinition, RewardDefinition, UserFeedbackEnvelope } from './types';

import { singularLabel } from './gamificationDesignerSupport';
import type { GamificationSelection } from './gamificationDesignerSupport';
export type { GamificationSelection } from './gamificationDesignerSupport';

const REPEAT_POLICY_OPTIONS = [
    { value: 'ONCE', label: 'Once', description: 'Grant this rule only once for each actor.' },
    { value: 'ONCE_PER_EVENT', label: 'Once per event', description: 'Grant at most once for each unique event and actor.' },
    { value: 'UNLIMITED', label: 'Unlimited', description: 'Grant every time the evidence matches, subject to explicit limits.' },
    { value: 'COOLDOWN', label: 'Cooldown', description: 'Require a minimum time between grants for the same actor.' },
    { value: 'DAILY_LIMIT', label: 'Daily limit', description: 'Allow grants until the configured per-day limit is reached.' }
] as const;

const EFFECT_KIND_OPTIONS = [
    { value: 'AWARD', label: 'Award points', description: 'Adds the configured amount to the selected point balance.' },
    { value: 'PENALTY', label: 'Apply penalty', description: 'Subtracts the configured amount without passing the point type minimum balance.' }
] as const;

const CONDITION_OPERATOR_OPTIONS = [
    { value: 'EQUALS', label: 'Equals', description: 'The value at the condition path must equal the expected value.' },
    { value: 'NOT_EQUALS', label: 'Does not equal', description: 'The value at the condition path must differ from the expected value.' },
    { value: 'CONTAINS', label: 'Contains', description: 'Text or collection data must contain the expected value.' },
    { value: 'GREATER_THAN', label: 'Greater than', description: 'The numeric value must be greater than the expected value.' },
    { value: 'LESS_THAN', label: 'Less than', description: 'The numeric value must be lower than the expected value.' },
    { value: 'EXISTS', label: 'Exists', description: 'The condition path must resolve to a value.' }
] as const;


export function GamificationConnectionSummary({ nodeId, nodes, edges, onSelectRelation }: { nodeId: string; nodes: Node<GamificationNodeData>[]; edges: Edge<GamificationEdgeData>[]; onSelectRelation: (edgeId: string) => void }) {
    const related = edges.filter(edge => edge.source === nodeId || edge.target === nodeId);
    const labels = new Map(nodes.map(node => [node.id, node.data.label]));
    return <section className="gamification-connection-summary" aria-label={`Visual connections for ${nodeId}`}>
        <header><div><h4>Visual connections</h4><p>Every connection is backed by the model property shown in the related elements.</p></div><span>{related.length}</span></header>
        <div>
            {related.map(edge => {
                const outgoing = edge.source === nodeId;
                const otherId = outgoing ? edge.target : edge.source;
                const otherLabel = labels.get(otherId) ?? otherId;
                return <HydraButton type="button" key={edge.id} title={`${outgoing ? 'Outgoing to' : 'Incoming from'} ${otherLabel}`} onClick={() => onSelectRelation(edge.id)}>
                    <strong>{outgoing ? 'Outgoing' : 'Incoming'}</strong>
                    <span>{otherLabel}</span>
                    <small>{String(edge.label ?? edge.id)} · {otherId} · select to inspect or remove the relation</small>
                </HydraButton>;
            })}
            {related.length === 0 && <p>No visual relation is connected to this element.</p>}
        </div>
    </section>;
}

export function GamificationEvidenceInspector({ eventTypeId, ruleCount, customEventTypes, onRename }: { eventTypeId: string; ruleCount: number; customEventTypes: EventTypeDefinition[]; onRename: (value: string) => void }) {
    const presentation = presentEventType(eventTypeId, customEventTypes);
    return <section className="gamification-evidence-inspector">
        <div><h3>{presentation.label}</h3><p>{presentation.description} It is consumed by {ruleCount} rule(s).</p></div>
        <DesignerHelpedField label="Event type ID" targetId="gamification-diagram-evidence-id" required help="HYDRA event that proves the modeled action occurred. The node is derived from rule bindings and is not a separate runtime command." example="Problem resolved">
            <input id="gamification-diagram-evidence-id" value={eventTypeId} onChange={event => onRename(event.target.value)} />
        </DesignerHelpedField>
        <p className="verified-evidence-note">Use outcome evidence such as <strong>Problem resolved</strong>. A button click or the mere fact that a quick fix was applied is not sufficient proof.</p>
    </section>;
}

export function GamificationRelationInspector({ edge, visual, onVisualChange, onDelete }: { edge: Edge<GamificationEdgeData>; visual: HydraEditableConnectionVisual; onVisualChange: (next: HydraEditableConnectionVisual) => void; onDelete: () => void }) {
    return <section className="gamification-edge-inspector">
        <div><h3>{visual.label || String(edge.label ?? 'Gamification relation')}</h3><p>{edge.data?.sourceKind} → {edge.data?.targetKind}</p></div>
        <DesignerHelpedField label="Source element" targetId="gamification-edge-source" help="HYDRA element from which this derived relationship starts." example="rule:verified-quality"><input id="gamification-edge-source" readOnly value={edge.data?.sourceElementId ?? edge.source} /></DesignerHelpedField>
        <DesignerHelpedField label="Target element" targetId="gamification-edge-target" help="HYDRA element referenced by the source definition or rule." example="points:quality"><input id="gamification-edge-target" readOnly value={edge.data?.targetElementId ?? edge.target} /></DesignerHelpedField>
        <HydraConnectionVisualEditor value={visual} onChange={onVisualChange} />
        <HydraButton className="hydra-danger-button" type="button" disabled={edge.data?.targetKind === 'LEDGER'} onClick={onDelete}>Remove relation</HydraButton>
    </section>;
}

export function GamificationLedgerInspector({ runtime }: { runtime: GamificationRuntimeSnapshot | null }) {
    return <section className="gamification-ledger-inspector"><div><h3>Runtime ledger</h3><p>Read-only trace generated from verified evidence and compensations.</p></div><strong>{runtime?.ledger.entries.length ?? 0} ledger entries</strong><span>{Object.keys(runtime?.state.actors ?? {}).length} actor(s)</span><small>Open “Runtime & ledger” for the complete audit trail.</small></section>;
}

export function GamificationElementForm({ selection, item, definitions, eventTypeIds, customEventTypes, onDefinitionsChange, onRuleChange, onRenameId, onDelete }: {
    selection: NonNullable<GamificationSelection>;
    item: PointTypeDefinition | LevelDefinition | AchievementDefinition | ChallengeDefinition | RewardDefinition | GamificationRule;
    definitions: GamificationDefinitionModel;
    eventTypeIds: string[];
    customEventTypes: EventTypeDefinition[];
    onDefinitionsChange: (next: GamificationDefinitionModel) => void;
    onRuleChange: (rule: GamificationRule) => void;
    onRenameId: (nextId: string) => void;
    onDelete: () => void;
}) {
    const commonHeader = (name: string, enabled?: boolean, onEnabled?: (value: boolean) => void) => (
        <><div className="hydra-designer-form-header"><div><h3>{name}</h3><p>{singularLabel(selection.kind)} · <span className="hydra-id-preview">{item.id}</span></p></div><div className="hydra-designer-form-actions">{enabled !== undefined && onEnabled !== undefined && <DesignerHelpedCheckbox className="compact" label="Enabled" targetId="gamification-element-enabled" help="Controls whether this evidence rule participates in runtime evaluation." example="Enabled" checked={enabled} onChange={onEnabled} />}<DesignerGuideButton kind="GAMIFICATION" compact /><HydraButton className="hydra-danger-button" type="button" onClick={onDelete}>Delete</HydraButton></div></div><DesignerRequiredFieldsLegend /></>
    );

    if (selection.kind === 'POINT') {
        const point = item as PointTypeDefinition;
        return <section className="gamification-editor">{commonHeader(point.name)}<div className="hydra-designer-fields">
            <DesignerHelpedField label="Name" targetId="gamification-point-name" required help="Visible name of this independent point scale." example="Quality points"><input id="gamification-point-name" value={point.name} onChange={event => onDefinitionsChange({ ...definitions, pointTypes: definitions.pointTypes.map(value => value.id === point.id ? { ...value, name: event.target.value } : value) })} /></DesignerHelpedField>
            <DesignerHelpedField label="Point type ID" targetId="gamification-point-id" required help="Stable identifier referenced by levels and evidence rules." example="points:quality"><input id="gamification-point-id" value={point.id} onChange={event => onRenameId(event.target.value)} /></DesignerHelpedField>
            <DesignerHelpedField label="Minimum balance" targetId="gamification-point-minimum" required help="Lowest balance permitted for this point scale. Use zero to prevent debt, or a negative value to allow bounded penalties." example="-20"><input id="gamification-point-minimum" type="number" value={point.minimumBalance ?? 0} onChange={event => onDefinitionsChange({ ...definitions, pointTypes: definitions.pointTypes.map(value => value.id === point.id ? { ...value, minimumBalance: Number(event.target.value) } : value) })} /></DesignerHelpedField>
            <DesignerHelpedField className="hydra-designer-wide" label="Description" targetId="gamification-point-description" help="Explain which verified behaviour this point scale measures." example="Points for improving model quality."><textarea id="gamification-point-description" value={point.description} onChange={event => onDefinitionsChange({ ...definitions, pointTypes: definitions.pointTypes.map(value => value.id === point.id ? { ...value, description: event.target.value } : value) })} /></DesignerHelpedField>
        </div></section>;
    }

    if (selection.kind === 'LEVEL') {
        const level = item as LevelDefinition;
        return <section className="gamification-editor">{commonHeader(level.name)}<div className="hydra-designer-fields">
            <DesignerHelpedField label="Name" targetId="gamification-level-name" required help="Visible progression level name." example="Model Guardian"><input id="gamification-level-name" value={level.name} onChange={event => onDefinitionsChange({ ...definitions, levels: definitions.levels.map(value => value.id === level.id ? { ...value, name: event.target.value } : value) })} /></DesignerHelpedField>
            <DesignerHelpedField label="Level ID" targetId="gamification-level-id" required help="Stable identifier of this level." example="level:model-guardian"><input id="gamification-level-id" value={level.id} onChange={event => onRenameId(event.target.value)} /></DesignerHelpedField>
            <DesignerHelpedField label="Point type" targetId="gamification-level-point-type" required help="Point scale used to calculate entry into this level." example="Quality points" options={definitions.pointTypes.map(point => ({ value: point.id, label: point.name, description: point.description || 'Named progression scale.' }))}><HydraAdaptiveSelect showOptionHelp={false} id="gamification-level-point-type" ariaLabel="Point type" required value={level.pointTypeId} options={definitions.pointTypes.map(point => ({ value: point.id, label: point.name, description: point.description || 'Named progression scale.', category: 'Point scales' }))} onChange={pointTypeId => onDefinitionsChange({ ...definitions, levels: definitions.levels.map(value => value.id === level.id ? { ...value, pointTypeId } : value) })} /></DesignerHelpedField>
            {level.pointTypeId !== '' && <DesignerHelpedField className="hydra-designer-compact" label="Minimum points" targetId="gamification-level-minimum" required help="Inclusive point threshold required to reach the level." example="100"><input id="gamification-level-minimum" type="number" min={0} value={level.minimumPoints} onChange={event => onDefinitionsChange({ ...definitions, levels: definitions.levels.map(value => value.id === level.id ? { ...value, minimumPoints: Number(event.target.value) } : value) })} /></DesignerHelpedField>}
            <DesignerHelpedField label="Reward" targetId="gamification-level-reward" help="Optional reward granted when the level is reached." example="Guardian badge" options={[{ value: '', label: 'No reward', description: 'Do not grant a reward for this level.' }, ...definitions.rewards.map(reward => ({ value: reward.id, label: reward.name, description: reward.description || 'Portable reward granted by gamification.' }))]}><HydraAdaptiveSelect showOptionHelp={false} id="gamification-level-reward" ariaLabel="Reward" value={level.rewardId} options={[{ value: '', label: 'No reward', description: 'Do not grant a reward for this level.', category: 'Recommended' }, ...definitions.rewards.map(reward => ({ value: reward.id, label: reward.name, description: reward.description || 'Portable reward granted by gamification.', category: 'Rewards' }))]} onChange={rewardId => onDefinitionsChange({ ...definitions, levels: definitions.levels.map(value => value.id === level.id ? { ...value, rewardId } : value) })} /></DesignerHelpedField>
        </div></section>;
    }

    if (selection.kind === 'ACHIEVEMENT') {
        const achievement = item as AchievementDefinition;
        return <section className="gamification-editor">{commonHeader(achievement.name)}<div className="hydra-designer-fields">
            <DesignerHelpedField label="Name" targetId="gamification-achievement-name" required help="Visible achievement name." example="First clean validation"><input id="gamification-achievement-name" value={achievement.name} onChange={event => onDefinitionsChange({ ...definitions, achievements: definitions.achievements.map(value => value.id === achievement.id ? { ...value, name: event.target.value } : value) })} /></DesignerHelpedField>
            <DesignerHelpedField label="Achievement ID" targetId="gamification-achievement-id" required help="Stable identifier referenced by evidence rules and the ledger." example="achievement:first-clean-validation"><input id="gamification-achievement-id" value={achievement.id} onChange={event => onRenameId(event.target.value)} /></DesignerHelpedField>
            <DesignerHelpedField className="hydra-designer-wide" label="Description" targetId="gamification-achievement-description" help="Explain the verified milestone represented by the achievement." example="Awarded when a model reaches zero validation errors."><textarea id="gamification-achievement-description" value={achievement.description} onChange={event => onDefinitionsChange({ ...definitions, achievements: definitions.achievements.map(value => value.id === achievement.id ? { ...value, description: event.target.value } : value) })} /></DesignerHelpedField>
            <DesignerHelpedField label="Reward" targetId="gamification-achievement-reward" help="Optional reward granted together with the achievement." example="Clean model badge" options={[{ value: '', label: 'No reward', description: 'Do not grant a separate reward.' }, ...definitions.rewards.map(reward => ({ value: reward.id, label: reward.name, description: reward.description || 'Portable reward granted by gamification.' }))]}><HydraAdaptiveSelect showOptionHelp={false} id="gamification-achievement-reward" ariaLabel="Reward" value={achievement.rewardId} options={[{ value: '', label: 'No reward', description: 'Do not grant a separate reward.', category: 'Recommended' }, ...definitions.rewards.map(reward => ({ value: reward.id, label: reward.name, description: reward.description || 'Portable reward granted by gamification.', category: 'Rewards' }))]} onChange={rewardId => onDefinitionsChange({ ...definitions, achievements: definitions.achievements.map(value => value.id === achievement.id ? { ...value, rewardId } : value) })} /></DesignerHelpedField>
            <DesignerHelpedCheckbox label="Hidden until earned" targetId="gamification-achievement-hidden" help="Keeps the achievement out of normal catalog views until an actor earns it." example="false" checked={achievement.hidden} onChange={hidden => onDefinitionsChange({ ...definitions, achievements: definitions.achievements.map(value => value.id === achievement.id ? { ...value, hidden } : value) })} />
        </div></section>;
    }

    if (selection.kind === 'CHALLENGE') {
        const challenge = item as ChallengeDefinition;
        return <section className="gamification-editor">{commonHeader(challenge.name)}<div className="hydra-designer-fields">
            <DesignerHelpedField label="Name" targetId="gamification-challenge-name" required help="Visible challenge name." example="Resolve ten diagnostics"><input id="gamification-challenge-name" value={challenge.name} onChange={event => onDefinitionsChange({ ...definitions, challenges: definitions.challenges.map(value => value.id === challenge.id ? { ...value, name: event.target.value } : value) })} /></DesignerHelpedField>
            <DesignerHelpedField label="Challenge ID" targetId="gamification-challenge-id" required help="Stable identifier referenced by evidence rules and progress state." example="challenge:resolve-ten"><input id="gamification-challenge-id" value={challenge.id} onChange={event => onRenameId(event.target.value)} /></DesignerHelpedField>
            <DesignerHelpedField className="hydra-designer-wide" label="Description" targetId="gamification-challenge-description" help="Explain the repeated verified behaviour required by this challenge." example="Resolve ten distinct validation diagnostics."><textarea id="gamification-challenge-description" value={challenge.description} onChange={event => onDefinitionsChange({ ...definitions, challenges: definitions.challenges.map(value => value.id === challenge.id ? { ...value, description: event.target.value } : value) })} /></DesignerHelpedField>
            <DesignerHelpedField label="Target count" targetId="gamification-challenge-target" required help="Number of qualifying evidence events needed to complete the challenge." example="10"><input id="gamification-challenge-target" type="number" min={1} value={challenge.targetCount} onChange={event => onDefinitionsChange({ ...definitions, challenges: definitions.challenges.map(value => value.id === challenge.id ? { ...value, targetCount: Number(event.target.value) } : value) })} /></DesignerHelpedField>
            <DesignerHelpedField label="Reward" targetId="gamification-challenge-reward" help="Optional reward granted on challenge completion." example="Quality streak badge" options={[{ value: '', label: 'No reward', description: 'Do not grant a separate reward.' }, ...definitions.rewards.map(reward => ({ value: reward.id, label: reward.name, description: reward.description || 'Portable reward granted by gamification.' }))]}><HydraAdaptiveSelect showOptionHelp={false} id="gamification-challenge-reward" ariaLabel="Reward" value={challenge.rewardId} options={[{ value: '', label: 'No reward', description: 'Do not grant a separate reward.', category: 'Recommended' }, ...definitions.rewards.map(reward => ({ value: reward.id, label: reward.name, description: reward.description || 'Portable reward granted by gamification.', category: 'Rewards' }))]} onChange={rewardId => onDefinitionsChange({ ...definitions, challenges: definitions.challenges.map(value => value.id === challenge.id ? { ...value, rewardId } : value) })} /></DesignerHelpedField>
        </div></section>;
    }

    if (selection.kind === 'REWARD') {
        const reward = item as RewardDefinition;
        return <section className="gamification-editor">{commonHeader(reward.name)}<div className="hydra-designer-fields">
            <DesignerHelpedField label="Name" targetId="gamification-reward-name" required help="Visible reward name." example="Guardian badge"><input id="gamification-reward-name" value={reward.name} onChange={event => onDefinitionsChange({ ...definitions, rewards: definitions.rewards.map(value => value.id === reward.id ? { ...value, name: event.target.value } : value) })} /></DesignerHelpedField>
            <DesignerHelpedField label="Reward ID" targetId="gamification-reward-id" required help="Stable identifier referenced by levels, achievements, challenges and rules." example="reward:guardian-badge"><input id="gamification-reward-id" value={reward.id} onChange={event => onRenameId(event.target.value)} /></DesignerHelpedField>
            <DesignerHelpedField className="hydra-designer-wide" label="Description" targetId="gamification-reward-description" help="Explain what the reward represents and how an adapter may present it." example="Unlocks the Model Guardian badge."><textarea id="gamification-reward-description" value={reward.description} onChange={event => onDefinitionsChange({ ...definitions, rewards: definitions.rewards.map(value => value.id === reward.id ? { ...value, description: event.target.value } : value) })} /></DesignerHelpedField>
            <DesignerHelpedField className="hydra-designer-wide" label="Payload JSON" targetId="gamification-reward-payload" help="Portable adapter-neutral reward data. Keep UI-specific rendering outside the main HYDRA model." example='{ "badge": "guardian" }'><textarea id="gamification-reward-payload" value={JSON.stringify(reward.payload, null, 2)} onChange={event => { try { onDefinitionsChange({ ...definitions, rewards: definitions.rewards.map(value => value.id === reward.id ? { ...value, payload: JSON.parse(event.target.value) as Record<string, unknown> } : value) }); } catch { /* Preserve the last valid payload. */ } }} /></DesignerHelpedField>
        </div></section>;
    }

    const rule = item as GamificationRule;
    return <section className="gamification-editor rule">{commonHeader(rule.name, rule.enabled, enabled => onRuleChange({ ...rule, enabled }))}<div className="hydra-designer-fields">
        <DesignerHelpedField label="Name" targetId="gamification-rule-name" required help="Readable name that states the verified evidence and outcome." example="Reward resolved diagnostic"><input id="gamification-rule-name" value={rule.name} onChange={event => onRuleChange({ ...rule, name: event.target.value })} /></DesignerHelpedField>
        <DesignerHelpedField label="Rule ID" targetId="gamification-rule-id" required help="Stable identifier stored in every ledger award produced by this rule." example="rule:resolved-diagnostic-points"><input id="gamification-rule-id" value={rule.id} onChange={event => onRenameId(event.target.value)} /></DesignerHelpedField>
        <DesignerHelpedField className="hydra-designer-wide" label="Verified event type" targetId="gamification-rule-event" required help="Verified HYDRA evidence consumed by the rule. Do not use a mere UI action; select a committed outcome event that proves the result." example="Problem resolved" options={eventTypeOptionHelp(eventTypeIds, customEventTypes)}><HydraAdaptiveSelect showOptionHelp={false} id="gamification-rule-event" ariaLabel="Verified event type" required value={rule.eventTypeId} options={eventTypeIds.map(eventTypeId => { const presentation = presentEventType(eventTypeId, customEventTypes); return { value: eventTypeId, label: presentation.label, description: presentation.description, category: presentation.category }; })} onChange={eventTypeId => onRuleChange({ ...rule, eventTypeId })} /></DesignerHelpedField>
        <DesignerHelpedField label="Condition path" targetId="gamification-rule-path" help="Optional path read from the HYDRA event. Leave it empty when the event type alone is sufficient evidence." example="payload.severity"><input id="gamification-rule-path" value={rule.conditionPath} onChange={event => onRuleChange({ ...rule, conditionPath: event.target.value, expectedValue: event.target.value.trim() === '' ? undefined : rule.expectedValue })} /></DesignerHelpedField>
        {rule.conditionPath.trim() !== '' && <DesignerHelpedField label="Operator" targetId="gamification-rule-operator" help="Comparison applied to the condition path. Friendly labels are shown while HYDRA keeps the neutral operator token internally." example="Equals" options={[...CONDITION_OPERATOR_OPTIONS]}><HydraAdaptiveSelect showOptionHelp={false} id="gamification-rule-operator" ariaLabel="Operator" value={rule.operator} options={CONDITION_OPERATOR_OPTIONS} onChange={operator => onRuleChange({ ...rule, operator })} /></DesignerHelpedField>}
        {rule.conditionPath.trim() !== '' && rule.operator !== 'EXISTS' && <DesignerHelpedField className="hydra-designer-wide" label="Expected value (JSON)" targetId="gamification-rule-expected" help="Comparison value used by the selected operator." example='"ERROR"'><input id="gamification-rule-expected" value={serialize(rule.expectedValue)} onChange={event => onRuleChange({ ...rule, expectedValue: parse(event.target.value) })} /></DesignerHelpedField>}
        <DesignerHelpedField label="Point type" targetId="gamification-rule-points-type" help="Point scale changed by this rule. Select No points only for an award rule that grants a non-point outcome." example="Quality points" options={[{ value: '', label: 'No points', description: 'The rule does not change a point balance.' }, ...definitions.pointTypes.map(point => ({ value: point.id, label: point.name, description: `${point.description || 'Named progression scale.'} Minimum balance: ${point.minimumBalance ?? 0}.` }))]}><HydraAdaptiveSelect showOptionHelp={false} id="gamification-rule-points-type" ariaLabel="Point type" value={rule.pointsTypeId} options={[{ value: '', label: 'No points', description: 'The rule does not change a point balance.', category: 'Recommended' }, ...definitions.pointTypes.map(point => ({ value: point.id, label: point.name, description: `${point.description || 'Named progression scale.'} Minimum balance: ${point.minimumBalance ?? 0}.`, category: 'Point scales' }))]} onChange={pointsTypeId => onRuleChange({ ...rule, pointsTypeId })} /></DesignerHelpedField>
        <DesignerHelpedField label="Effect" targetId="gamification-rule-effect" required help="Choose whether verified evidence awards points or applies a penalty. Penalties are recorded as negative ledger entries and respect the point scale minimum balance." example="Apply penalty" options={[...EFFECT_KIND_OPTIONS]}><HydraAdaptiveSelect showOptionHelp={false} id="gamification-rule-effect" ariaLabel="Effect" required value={rule.effectKind ?? (rule.points < 0 ? 'PENALTY' : 'AWARD')} options={EFFECT_KIND_OPTIONS} onChange={effectKind => onRuleChange({ ...rule, effectKind: effectKind as GamificationRule['effectKind'], points: effectKind === 'PENALTY' ? -Math.abs(rule.points || 1) : Math.abs(rule.points), achievementId: effectKind === 'PENALTY' ? '' : rule.achievementId, challengeId: effectKind === 'PENALTY' ? '' : rule.challengeId, rewardId: effectKind === 'PENALTY' ? '' : rule.rewardId })} /></DesignerHelpedField>
        {rule.pointsTypeId !== '' && <DesignerHelpedField className="hydra-designer-compact" label="Point amount" targetId="gamification-rule-points" help="Absolute amount to add or subtract. HYDRA stores penalties as a negative compensable ledger entry." example="3"><input id="gamification-rule-points" type="number" min={0} value={Math.abs(rule.points)} onChange={event => { const amount = Math.abs(Number(event.target.value)); onRuleChange({ ...rule, points: (rule.effectKind ?? (rule.points < 0 ? 'PENALTY' : 'AWARD')) === 'PENALTY' ? -amount : amount }); }} /></DesignerHelpedField>}
        {rule.effectKind !== 'PENALTY' && <><DesignerHelpedField label="Achievement" targetId="gamification-rule-achievement" help="Optional achievement awarded by this verified event." example="First clean validation" options={[{ value: '', label: 'No achievement', description: 'The rule does not grant an achievement.' }, ...definitions.achievements.map(value => ({ value: value.id, label: value.name, description: value.description || 'One-off verifiable accomplishment.' }))]}><HydraAdaptiveSelect showOptionHelp={false} id="gamification-rule-achievement" ariaLabel="Achievement" value={rule.achievementId} options={[{ value: '', label: 'No achievement', description: 'The rule does not grant an achievement.', category: 'Recommended' }, ...definitions.achievements.map(value => ({ value: value.id, label: value.name, description: value.description || 'One-off verifiable accomplishment.', category: 'Achievements' }))]} onChange={achievementId => onRuleChange({ ...rule, achievementId })} /></DesignerHelpedField>
        <DesignerHelpedField label="Challenge" targetId="gamification-rule-challenge" help="Optional challenge whose progress increases for this evidence." example="Resolve ten problems" options={[{ value: '', label: 'No challenge', description: 'The rule does not advance a challenge.' }, ...definitions.challenges.map(value => ({ value: value.id, label: value.name, description: value.description || 'Progress target advanced by verified evidence.' }))]}><HydraAdaptiveSelect showOptionHelp={false} id="gamification-rule-challenge" ariaLabel="Challenge" value={rule.challengeId} options={[{ value: '', label: 'No challenge', description: 'The rule does not advance a challenge.', category: 'Recommended' }, ...definitions.challenges.map(value => ({ value: value.id, label: value.name, description: value.description || 'Progress target advanced by verified evidence.', category: 'Challenges' }))]} onChange={challengeId => onRuleChange({ ...rule, challengeId })} /></DesignerHelpedField>
        <DesignerHelpedField label="Reward" targetId="gamification-rule-reward" help="Optional direct reward granted by the rule." example="Guardian badge" options={[{ value: '', label: 'No reward', description: 'The rule does not grant a direct reward.' }, ...definitions.rewards.map(value => ({ value: value.id, label: value.name, description: value.description || 'Portable reward granted by gamification.' }))]}><HydraAdaptiveSelect showOptionHelp={false} id="gamification-rule-reward" ariaLabel="Reward" value={rule.rewardId} options={[{ value: '', label: 'No reward', description: 'The rule does not grant a direct reward.', category: 'Recommended' }, ...definitions.rewards.map(value => ({ value: value.id, label: value.name, description: value.description || 'Portable reward granted by gamification.', category: 'Rewards' }))]} onChange={rewardId => onRuleChange({ ...rule, rewardId })} /></DesignerHelpedField></>}
        <DesignerHelpedField label="Repeat policy" targetId="gamification-rule-repeat" required help="Idempotence and repetition strategy for matching evidence." example="Once per event" options={[...REPEAT_POLICY_OPTIONS]}><HydraAdaptiveSelect showOptionHelp={false} id="gamification-rule-repeat" ariaLabel="Repeat policy" value={rule.repeatPolicy} options={REPEAT_POLICY_OPTIONS} onChange={repeatPolicy => onRuleChange({ ...rule, repeatPolicy: repeatPolicy as GamificationRule['repeatPolicy'] })} /></DesignerHelpedField>
        {rule.repeatPolicy === 'COOLDOWN' && <DesignerHelpedField className="hydra-designer-compact" label="Cooldown seconds" targetId="gamification-rule-cooldown" help="Minimum time between applications for cooldown-based rules." example="3600"><input id="gamification-rule-cooldown" type="number" min={0} value={rule.cooldownSeconds} onChange={event => onRuleChange({ ...rule, cooldownSeconds: Number(event.target.value) })} /></DesignerHelpedField>}
        <DesignerHelpedField className="hydra-designer-compact" label="Max per actor" targetId="gamification-rule-max-actor" help="Lifetime application limit per actor for both awards and penalties. Zero means no explicit lifetime limit." example="10"><input id="gamification-rule-max-actor" type="number" min={0} value={rule.maxAwardsPerActor} onChange={event => onRuleChange({ ...rule, maxAwardsPerActor: Number(event.target.value) })} /></DesignerHelpedField>
        {rule.repeatPolicy === 'DAILY_LIMIT' && <DesignerHelpedField className="hydra-designer-compact" label="Max per day" targetId="gamification-rule-max-day" help="Daily anti-farming limit per actor." example="5"><input id="gamification-rule-max-day" type="number" min={0} value={rule.maxAwardsPerDay} onChange={event => onRuleChange({ ...rule, maxAwardsPerDay: Number(event.target.value) })} /></DesignerHelpedField>}
        <p className="verified-evidence-note hydra-designer-wide">Awards and penalties must be based on verified outcomes such as <strong>Problem resolved</strong>, not merely on pressing or applying a quick fix.</p>
    </div></section>;
}

export function GamificationLedgerView({ runtime, feedback, onRefresh, onReset }: { runtime: GamificationRuntimeSnapshot | null; feedback: UserFeedbackEnvelope[]; onRefresh: () => Promise<void>; onReset: () => Promise<void> }) {
    const {t}=useHydraLanguage();
    return <div className="gamification-ledger"><header><h3>{t('Runtime state, feedback and compensable ledger')}</h3><div><HydraButton type="button" onClick={() => void onRefresh()}>{t('Refresh')}</HydraButton><HydraButton className="hydra-danger-button" type="button" onClick={() => void onReset()}>{t('Reset')}</HydraButton></div></header><div className="gamification-actor-grid">{Object.values(runtime?.state.actors ?? {}).map(actor => <article key={actor.actorId}><strong>{actor.actorId}</strong><span>{Object.entries(actor.points).map(([type, points]) => `${type}: ${points}`).join(' · ') || t('No points')}</span><small>{actor.achievementIds.length} {t('achievement(s)')} · {actor.rewardIds.length} {t('reward(s)')}</small></article>)}</div><section className="gamification-feedback-ledger" aria-label={t('Generated user feedback')}><header><div><h4>{t('User feedback')}</h4><p>{t('Portable feedback projected deterministically from the compensable ledger.')}</p></div><span>{feedback.length}</span></header>{feedback.length===0?<p>{t('No feedback has been generated yet.')}</p>:<div className="gamification-feedback-ledger-list">{feedback.slice(0,100).map(item=><article key={item.id} className={`severity-${item.severity.toLowerCase()}`}><div><strong>{item.title}</strong><small>{item.kind} · {item.actorId}</small></div><span>{item.message}</span>{item.pointsDelta!==0&&<b>{item.pointsDelta>0?'+':''}{item.pointsDelta} {item.pointTypeId}</b>}{item.progressTarget>0&&<progress value={Math.min(item.progressCurrent,item.progressTarget)} max={item.progressTarget}/>} {item.nextStep&&<small>{item.nextStep}</small>}</article>)}</div>}</section><table><thead><tr><th>{t('Event')}</th><th>{t('Rule')}</th><th>{t('Actor')}</th><th>{t('Effect')}</th><th>{t('Points')}</th><th>{t('Status')}</th><th>{t('Reason')}</th></tr></thead><tbody>{(runtime?.ledger.entries ?? []).slice().reverse().map(entry => <tr key={entry.id}><td>{entry.sourceEventId}</td><td>{entry.ruleId}</td><td>{entry.actorId}</td><td>{entry.pointsDelta < 0 ? t('Penalty') : t('Award')}</td><td>{entry.pointsDelta}</td><td>{entry.status}</td><td>{entry.metadata.reason ?? ''}</td></tr>)}</tbody></table></div>;
}





function serialize(value: unknown): string { return value === undefined ? '' : JSON.stringify(value); }
function parse(value: string): unknown { if (value.trim() === '') return undefined; try { return JSON.parse(value) as unknown; } catch { return value; } }
