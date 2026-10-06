import { describe, expect, it } from 'vitest';
import { clampTextPosition, clampTextRange, clampedPositionToOffset } from './grammarTextRange';

describe('grammar text range safety',()=>{
    it('clamps stale positions after destructive edits',()=>{
        const text='Model: ID;';
        expect(clampTextPosition(text,{line:50,column:200})).toEqual({line:0,column:text.length});
        expect(clampedPositionToOffset(text,{line:50,column:200})).toBe(text.length);
    });

    it('never returns a reversed range',()=>{
        expect(clampTextRange('a\nb',{start:{line:1,column:1},end:{line:0,column:0}})).toEqual({start:{line:1,column:1},end:{line:1,column:1}});
    });

    it('handles empty and CRLF documents without throwing',()=>{
        expect(clampedPositionToOffset('',{line:9,column:9})).toBe(0);
        expect(clampedPositionToOffset('a\r\nb',{line:1,column:1})).toBe(4);
    });
});
