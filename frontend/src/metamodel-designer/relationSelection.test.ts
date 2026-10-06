import { describe, expect, it } from 'vitest';

import { isAreaSelectionBackgroundTarget, isMetamodelRelationInteractionTarget, shouldClearMetamodelRelationFocus } from './relationSelection';

function buildPane(): HTMLDivElement {
    const pane = document.createElement('div');
    pane.className = 'react-flow__pane';
    document.body.appendChild(pane);
    return pane;
}

describe('relation selection target classification', () => {
    it('recognizes the transparent relationship hitbox', () => {
        const pane = buildPane();
        const hitbox = document.createElement('path');
        hitbox.setAttribute('data-metamodel-relation-interaction', 'true');
        pane.appendChild(hitbox);
        expect(isMetamodelRelationInteractionTarget(hitbox)).toBe(true);
    });

    it('recognizes the relationship label group', () => {
        const pane = buildPane();
        const label = document.createElement('g');
        label.setAttribute('data-metamodel-relation-interaction', 'true');
        pane.appendChild(label);
        expect(isMetamodelRelationInteractionTarget(label)).toBe(true);
    });

    it('recognizes descendants of a relationship label', () => {
        const pane = buildPane();
        const label = document.createElement('g');
        label.setAttribute('data-metamodel-relation-interaction', 'true');
        const rectangle = document.createElement('rect');
        label.appendChild(rectangle);
        pane.appendChild(label);
        expect(isMetamodelRelationInteractionTarget(rectangle)).toBe(true);
    });

    it('recognizes the rendered relationship group as a fallback', () => {
        const pane = buildPane();
        const group = document.createElement('g');
        group.className = 'metamodel-rendered-relation';
        const path = document.createElement('path');
        group.appendChild(path);
        pane.appendChild(group);
        expect(isMetamodelRelationInteractionTarget(path)).toBe(true);
    });

    it('does not treat the pane background as a relationship', () => {
        const pane = buildPane();
        expect(isMetamodelRelationInteractionTarget(pane)).toBe(false);
    });

    it('allows area selection on the pane background', () => {
        const pane = buildPane();
        expect(isAreaSelectionBackgroundTarget(pane)).toBe(true);
    });

    it('blocks area selection on a relationship hitbox', () => {
        const pane = buildPane();
        const hitbox = document.createElement('path');
        hitbox.setAttribute('data-metamodel-relation-interaction', 'true');
        pane.appendChild(hitbox);
        expect(isAreaSelectionBackgroundTarget(hitbox)).toBe(false);
    });

    it('blocks area selection on the relationship label rectangle', () => {
        const pane = buildPane();
        const label = document.createElement('g');
        label.setAttribute('data-metamodel-relation-interaction', 'true');
        const rectangle = document.createElement('rect');
        label.appendChild(rectangle);
        pane.appendChild(label);
        expect(isAreaSelectionBackgroundTarget(rectangle)).toBe(false);
    });

    it('blocks area selection on a metamodel node', () => {
        const pane = buildPane();
        const node = document.createElement('div');
        node.className = 'react-flow__node';
        pane.appendChild(node);
        expect(isAreaSelectionBackgroundTarget(node)).toBe(false);
    });

    it('rejects non-element event targets', () => {
        expect(isAreaSelectionBackgroundTarget(document)).toBe(false);
        expect(isMetamodelRelationInteractionTarget(null)).toBe(false);
    });
});

describe('relationship focus release classification', () => {
    it('does not clear focus when no relationship is selected', () => {
        const pane = buildPane();
        expect(shouldClearMetamodelRelationFocus(false, pane)).toBe(false);
    });

    it('clears focus when the canvas pane is pressed', () => {
        const pane = buildPane();
        expect(shouldClearMetamodelRelationFocus(true, pane)).toBe(true);
    });

    it('clears focus when a metamodel node is pressed', () => {
        const node = document.createElement('div');
        node.className = 'react-flow__node';
        expect(shouldClearMetamodelRelationFocus(true, node)).toBe(true);
    });

    it('clears focus when the canvas toolbar is pressed', () => {
        const toolbar = document.createElement('button');
        toolbar.className = 'metamodel-canvas-interaction-toolbar';
        expect(shouldClearMetamodelRelationFocus(true, toolbar)).toBe(true);
    });

    it('clears focus when React Flow controls are pressed', () => {
        const control = document.createElement('button');
        control.className = 'react-flow__controls';
        expect(shouldClearMetamodelRelationFocus(true, control)).toBe(true);
    });

    it('clears focus when the minimap is pressed', () => {
        const minimap = document.createElement('div');
        minimap.className = 'react-flow__minimap';
        expect(shouldClearMetamodelRelationFocus(true, minimap)).toBe(true);
    });

    it('keeps focus when the relationship line is pressed', () => {
        const hitbox = document.createElement('path');
        hitbox.setAttribute('data-metamodel-relation-interaction', 'true');
        expect(shouldClearMetamodelRelationFocus(true, hitbox)).toBe(false);
    });

    it('keeps focus when a relationship label descendant is pressed', () => {
        const label = document.createElement('g');
        label.setAttribute('data-metamodel-relation-interaction', 'true');
        const text = document.createElement('text');
        label.appendChild(text);
        expect(shouldClearMetamodelRelationFocus(true, text)).toBe(false);
    });

    it('keeps focus when an orthogonal route point is pressed', () => {
        const point = document.createElement('circle');
        point.setAttribute('data-metamodel-relation-interaction', 'true');
        expect(shouldClearMetamodelRelationFocus(true, point)).toBe(false);
    });

    it('ignores non-element targets safely', () => {
        expect(shouldClearMetamodelRelationFocus(true, document)).toBe(false);
        expect(shouldClearMetamodelRelationFocus(true, null)).toBe(false);
    });
});
