import { useMemo } from 'react';
import { DesignerStructureTree } from '../designer-ui/DesignerStructureTree';
import type { MetaModel } from '../metamodel-designer/types';
import type { ActionModel, ExpressionModel, OperationBehaviorModel, ValidationModel } from '../validation/types';
import { buildValidationStructure } from './validationStructureModel';

import type { ValidationStructureSelection } from './validationStructureTypes';
export type { ValidationStructureSelection } from './validationStructureTypes';

interface Props {
    metamodel: MetaModel;
    validations: ValidationModel;
    expressions: ExpressionModel;
    actions: ActionModel;
    behaviors: OperationBehaviorModel;
    verifiedRuleIds?: ReadonlySet<string>;
    selection: ValidationStructureSelection;
    filter: string;
    onSelect: (selection: Exclude<ValidationStructureSelection, null>) => void;
}

export function ValidationStructureTree({ metamodel, validations, expressions, actions, behaviors, verifiedRuleIds = EMPTY_VERIFIED_RULES, selection, filter, onSelect }: Props) {
    const nodes = useMemo(() => buildValidationStructure(metamodel, validations, expressions, actions, behaviors, verifiedRuleIds),
        [actions, behaviors, expressions, metamodel, validations, verifiedRuleIds]);
    const defaultExpandedIds = useMemo(() => [
        'validation-root',
        'validation-rule-sets',
        'validation-ungrouped-rules',
        'validation-actions',
        'validation-operations',
        ...validations.ruleSets.map(ruleSet => `validation:RULE_SET:${ruleSet.id}`)
    ], [validations.ruleSets]);
    return <DesignerStructureTree
        ariaLabel="Validation structure tree"
        nodes={nodes}
        selectedId={selection === null ? '' : `validation:${selection.kind}:${selection.id}`}
        filter={filter}
        defaultExpandedIds={defaultExpandedIds}
        onSelect={node => { if (node.data !== null && node.data !== undefined) onSelect(node.data); }}
    />;
}

const EMPTY_VERIFIED_RULES: ReadonlySet<string> = new Set();
