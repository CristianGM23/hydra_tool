import { describe, expect, it } from 'vitest';
import { EDITOR_CAPABILITY_HELP } from './editorCapabilityHelp';

const expected = ['MODEL_EDITING','MULTIPLE_REPRESENTATIONS','OPERATIONS','VALIDATION_RUNTIME','QUICK_FIX_RUNTIME','EVENTS','EVENT_FLOWS','GAMIFICATION','IMPORT','EXPORT','UNDO_REDO'];

describe('editor capability help', () => {
    it('explains every supported capability in user-facing language', () => {
        expect(EDITOR_CAPABILITY_HELP.map(item => item.value)).toEqual(expected);
        expect(EDITOR_CAPABILITY_HELP.every(item => item.label !== item.value && item.description.length > 30)).toBe(true);
    });
});
