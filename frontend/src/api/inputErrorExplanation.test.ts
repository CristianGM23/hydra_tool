import { describe, expect, it } from 'vitest';
import { explainInputError } from './inputErrorExplanation';

describe('input error explanation', () => {
    it('explains invalid JSON with concrete checks', () => {
        const result = explainInputError(new SyntaxError('Expected comma'), 'Invalid JSON', 'the Event source tab');
        expect(result).toContain('What went wrong:');
        expect(result).toContain('double quotes');
        expect(result).toContain('Event source tab');
    });
    it('explains stable document IDs', () => {
        expect(explainInputError(new Error('id must be a stable document id'), 'Invalid ID')).toContain('Spaces, colons and slashes are not allowed');
    });
    it('adds a correction path to generic form errors', () => {
        expect(explainInputError(new Error('Value rejected'), 'Failed', 'Integration properties')).toContain('Technical message preserved');
    });
    it('turns mapping failures into an ordered correction recipe while preserving the concrete value', () => {
        const result=explainInputError(new Error("binding id=personName has unknown target feature 'fullName'"),'Mapping failed','Transformation mapping');
        expect(result).toContain('source type');
        expect(result).toContain('target feature');
        expect(result).toContain('fullName');
    });
});
