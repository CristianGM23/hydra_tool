import { describe, expect, it } from 'vitest';
import { applyEventConnection } from './eventConnections';
import { createDefaultEventType, createEmptyEventModels } from './eventDesignerFactory';

describe('visual event connections', () => {
    function fixture(){ const value=createEmptyEventModels('people'); const target={...createDefaultEventType(value.definitions.namespace),id:'event:target'}; value.definitions.eventTypes=[target]; return value; }
    it('creates a canonical binding from a visual source-to-target link', () => { const value=fixture(); const result=applyEventConnection(value.definitions,value.bindings,['core.model.saved','event:target'],'core.model.saved','event:target'); expect(result.accepted).toBe(true); expect(result.binding?.sourceEventTypeId).toBe('core.model.saved'); expect(result.binding?.targetEventTypeId).toBe('event:target'); expect(result.bindings.bindings).toHaveLength(1); });
    it('returns the new binding so the properties inspector can select its edge', () => { const value=fixture(); const result=applyEventConnection(value.definitions,value.bindings,['core.model.saved'],'core.model.saved','event:target'); expect(result.binding?.id).toMatch(/^binding:/); });
    it('rejects targets that are not editable custom event types', () => { const value=fixture(); const result=applyEventConnection(value.definitions,value.bindings,['core.model.saved','core.validation.started'],'core.model.saved','core.validation.started'); expect(result.accepted).toBe(false); expect(result.message).toContain('custom event type'); });
    it('rejects duplicate visual bindings', () => { const value=fixture(); const first=applyEventConnection(value.definitions,value.bindings,['core.model.saved'],'core.model.saved','event:target'); const second=applyEventConnection(value.definitions,first.bindings,['core.model.saved'],'core.model.saved','event:target'); expect(second.accepted).toBe(false); expect(second.bindings).toBe(first.bindings); });
    it('rejects sources outside the current event catalog', () => { const value=fixture(); const result=applyEventConnection(value.definitions,value.bindings,['core.model.saved'],'core.unknown','event:target'); expect(result.accepted).toBe(false); expect(result.message).toContain('source event'); });
});
