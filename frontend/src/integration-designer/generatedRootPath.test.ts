import { describe, expect, it } from 'vitest';
import { generatedRootProblemMessage, toProjectRelativeGeneratedRoot, validateGeneratedRootPath } from './generatedRootPath';

describe('generated integration root', () => {
    it('accepts portable relative folders and normalizes separators', () => {
        expect(validateGeneratedRootPath(' tools\\generated\\emf/ ')).toEqual({ valid: true, normalized: 'tools/generated/emf' });
    });

    it.each(['../outside', './generated/../outside', '/tmp/generated', 'C:\\tmp\\generated', 'file://tmp/generated'])(
        'rejects paths that can escape or bind the profile to one machine: %s',
        value => expect(validateGeneratedRootPath(value).valid).toBe(false)
    );

    it('rejects characters that are not portable to Windows project checkouts', () => {
        const result = validateGeneratedRootPath('generated/a:b');
        expect(result.problem).toBe('UNSAFE_CHARACTER');
        expect(generatedRootProblemMessage(result.problem)).toMatch(/portable/i);
    });

    it('derives a Windows path relative to the project without depending on drive-letter casing', () => {
        expect(toProjectRelativeGeneratedRoot('C:\\Repos\\People', 'c:\\repos\\people\\tools\\generated')).toEqual({
            valid: true,
            relativePath: 'tools/generated',
            message: ''
        });
    });

    it('blocks both an external folder and generation directly into the project root', () => {
        expect(toProjectRelativeGeneratedRoot('/repo/people', '/repo/other').valid).toBe(false);
        expect(toProjectRelativeGeneratedRoot('/repo/people', '/repo/people')).toEqual(expect.objectContaining({ valid: false, message: expect.stringMatching(/subfolder/i) }));
    });
    it('rejects Windows device names and trailing-dot or trailing-space segments', () => {
        expect(validateGeneratedRootPath('generated/CON').valid).toBe(false);
        expect(validateGeneratedRootPath('generated/com1.txt').valid).toBe(false);
        expect(validateGeneratedRootPath('generated/folder.').valid).toBe(false);
        expect(validateGeneratedRootPath('generated/folder ').valid).toBe(false);
    });

});
