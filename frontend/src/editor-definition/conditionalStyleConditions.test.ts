import { describe, expect, it } from 'vitest';
import { conditionDuplicatesAnotherRule, conditionalStyleConditionFingerprint, duplicateConditionalStyleRuleIds } from './conditionalStyleConditions';
import type { ConditionalStyleRule } from './types';
import type { ExpressionDefinition, ExpressionModel } from '../validation/types';

function expression(id:string,nodeId:string,value:boolean):ExpressionDefinition {
    return { id, name:id, contextClassId:'Person', declaredReturnType:{kind:'BOOLEAN'}, root:{kind:'LITERAL',nodeId,valueType:{kind:'BOOLEAN'},value}, annotations:{} };
}
function model(expressions:ExpressionDefinition[]):ExpressionModel { return {schemaVersion:'1.0',id:'people',metamodelId:'people',expressions,annotations:{}}; }
function rule(id:string,conditionExpressionId:string):ConditionalStyleRule { return {id,name:id,enabled:true,priority:0,conditionExpressionId,styles:{}}; }

describe('conditional style condition identity',()=>{
    it('ignores generated expression and node ids',()=>{
        expect(conditionalStyleConditionFingerprint(expression('one','node:one',true))).toBe(conditionalStyleConditionFingerprint(expression('two','node:two',true)));
    });
    it('distinguishes different condition trees',()=>{
        expect(conditionalStyleConditionFingerprint(expression('one','node:one',true))).not.toBe(conditionalStyleConditionFingerprint(expression('two','node:two',false)));
    });
    it('marks every rule that reuses the same condition',()=>{
        const expressions=[expression('expression:one','node:one',true),expression('expression:two','node:two',true)];
        const rules=[rule('style:one','expression:one'),rule('style:two','expression:two')];
        expect([...duplicateConditionalStyleRuleIds(rules,model(expressions))].sort()).toEqual(['style:one','style:two']);
        expect(conditionDuplicatesAnotherRule('style:two',expressions[1],rules,model(expressions))).toBe(true);
    });
});
