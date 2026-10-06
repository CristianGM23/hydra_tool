import { describe, expect, it } from 'vitest';
import {
    blockConnectionChecks,
    makeBlocklyEnvelope,
    parseBlocklyBlockDefinitions,
    parseBlocklyLayerEnvelope,
    parseBlocklyEventDeclarations,
    serializeBlocklyLayerJavaScript
} from './blocklyInterchange';

describe('Blockly declarative interchange', () => {
    it('reads modern Blockly.common static block definitions and preserves connection checks', () => {
        const source = `Blockly.common.defineBlocksWithJsonArray([{
          "type":"number_add",
          "message0":"add %1",
          "args0":[{"type":"input_value","name":"VALUE","check":["Number","Integer"]}],
          "output":"Number"
        }]);`;
        const parsed = parseBlocklyBlockDefinitions(source);
        expect(parsed.warnings).toEqual([]);
        expect(parsed.definitions).toHaveLength(1);
        expect(parsed.definitions[0].type).toBe('number_add');
        expect(blockConnectionChecks(parsed.definitions[0])).toEqual(['VALUE:Number', 'VALUE:Integer', 'output:Number']);
    });

    it('reads legacy Blockly.Blocks jsonInit definitions without executing JavaScript', () => {
        const source = `Blockly.Blocks['legacy_text'] = {
          init: function() {
            this.jsonInit({"message0":"legacy %1","args0":[{"type":"field_input","name":"TEXT"}],"previousStatement":"Statement","nextStatement":"Statement"});
            window.__mustNeverRun = true;
          }
        };`;
        const parsed = parseBlocklyBlockDefinitions(source);
        expect(parsed.definitions).toHaveLength(1);
        expect(parsed.definitions[0].type).toBe('legacy_text');
        expect(blockConnectionChecks(parsed.definitions[0])).toEqual(['previous:Statement', 'next:Statement']);
        expect((globalThis as Record<string, unknown>).__mustNeverRun).toBeUndefined();
    });

    it('round-trips an independent layer through the passive JavaScript envelope', () => {
        const envelope = makeBlocklyEnvelope('VALIDATIONS', 'people', { rules: [{ id: 'adult' }] });
        const javascript = serializeBlocklyLayerJavaScript(envelope);
        expect(javascript).toContain('HYDRA_BLOCKLY_LAYER_BEGIN');
        expect(javascript).toContain("bridge.registerLayer(layer.layer, layer)");
        expect(javascript).toContain("new root.CustomEvent('hydra:blockly-layer'");
        expect(javascript).toContain('registerAdapter');
        expect(javascript).toContain('getLayer');
        expect(envelope.annotations['hydra.blockly.preferredArtifact']).toBe('javascript');
        const parsed = parseBlocklyLayerEnvelope<{ rules: Array<{ id: string }> }>(javascript);
        expect(parsed?.layer).toBe('VALIDATIONS');
        expect(parsed?.payload.rules[0].id).toBe('adult');
    });

    it('generates a metamodel registration script compatible with current and legacy Blockly registration APIs', () => {
        const envelope = makeBlocklyEnvelope('METAMODEL', 'people', { metamodel: { id: 'people' } }, [{ type: 'person' }]);
        const javascript = serializeBlocklyLayerJavaScript(envelope);
        expect(javascript).toContain('root.Blockly.common.defineBlocksWithJsonArray');
        expect(javascript).toContain('root.Blockly.defineBlocksWithJsonArray');
    });
    it('statically identifies Blockly JavaScript event categories without executing source code', () => {
        const source = `
            Blockly.Events.BLOCK_CREATE = 'create';
            export const VAR_RENAME = 'var_rename';
            workspace.addChangeListener(event => event.type === Blockly.Events.VIEWPORT_CHANGE);
            workspace.addChangeListener(event => event.type === 'finished_loading');
            Blockly.registry.register(Blockly.registry.Type.EVENT, 'my_custom_event', CustomEvent);
            globalThis.__hydraMustNeverExecute = true;
        `;
        const parsed = parseBlocklyEventDeclarations(source);
        expect(parsed.events.map(event => event.category)).toEqual(expect.arrayContaining(['BLOCK', 'VARIABLE', 'UI', 'WORKSPACE', 'CUSTOM']));
        expect(parsed.events.some(event => event.eventType === 'my_custom_event')).toBe(true);
        expect((globalThis as Record<string, unknown>).__hydraMustNeverExecute).toBeUndefined();
    });

});
