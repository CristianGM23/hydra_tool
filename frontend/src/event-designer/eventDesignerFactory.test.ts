import { describe,expect,it } from 'vitest';
import { createDefaultEventBinding,createDefaultEventField,createDefaultEventType,createEmptyEventModels } from './eventDesignerFactory';
describe('event designer factory',()=>{
 it('creates matching definition and binding documents',()=>{const result=createEmptyEventModels('people');expect(result.bindings.eventDefinitionModelId).toBe(result.definitions.id);expect(result.definitions.metamodelId).toBe('people');});
 it('creates namespaced event types',()=>{expect(createDefaultEventType('course.people').namespace).toBe('course.people');});
 it('creates payload fields with stable ids',()=>{expect(createDefaultEventField().id).toMatch(/^field:/);});
 it('creates bindings between source and target event types',()=>{const binding=createDefaultEventBinding('core.model.saved','course.people.saved');expect(binding.sourceEventTypeId).toBe('core.model.saved');expect(binding.targetEventTypeId).toBe('course.people.saved');});
});
