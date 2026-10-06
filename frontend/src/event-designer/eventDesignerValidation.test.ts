import { describe,expect,it } from 'vitest';
import { createDefaultEventBinding,createDefaultEventField,createDefaultEventType,createEmptyEventModels } from './eventDesignerFactory';
import { findEventDesignerBlockingError } from './eventDesignerValidation';
describe('event designer validation',()=>{
 it('accepts empty compatible documents',()=>{const value=createEmptyEventModels('people');expect(findEventDesignerBlockingError(value.definitions,value.bindings)).toBe('');});
 it('rejects mismatched definition references',()=>{const value=createEmptyEventModels('people');value.bindings.eventDefinitionModelId='other';expect(findEventDesignerBlockingError(value.definitions,value.bindings)).toContain('reference');});
 it('rejects duplicate event types',()=>{const value=createEmptyEventModels('people');const type=createDefaultEventType(value.definitions.namespace);value.definitions.eventTypes=[type,type];expect(findEventDesignerBlockingError(value.definitions,value.bindings)).toContain('duplicated');});
 it('requires target custom event types',()=>{const value=createEmptyEventModels('people');value.bindings.bindings=[createDefaultEventBinding('core.model.saved','unknown')];expect(findEventDesignerBlockingError(value.definitions,value.bindings)).toContain('custom event type');});
 it('requires mappings for required fields',()=>{const value=createEmptyEventModels('people');const type={...createDefaultEventType(value.definitions.namespace),fields:[{...createDefaultEventField(),required:true}]};value.definitions.eventTypes=[type];value.bindings.bindings=[createDefaultEventBinding('core.model.saved',type.id)];expect(findEventDesignerBlockingError(value.definitions,value.bindings)).toContain('required field');});
});
