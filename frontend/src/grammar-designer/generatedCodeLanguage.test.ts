import { describe, expect, it } from 'vitest';
import { generatedCodeCustomization, generatedCodeLanguageProfile } from './generatedCodeLanguage';

describe('generated code language profiles', () => {
    it('detects the generated host language from the file path', () => {
        expect(generatedCodeLanguageProfile('src/main.ts').languageId).toBe('typescript');
        expect(generatedCodeLanguageProfile('src/Main.java').languageId).toBe('java');
        expect(generatedCodeLanguageProfile('package.json').languageId).toBe('json');
        expect(generatedCodeLanguageProfile('grammar/Main.g4').languageId).toBe('antlr');
        expect(generatedCodeLanguageProfile('grammar/main.xtext').languageId).toBe('xtext');
        expect(generatedCodeLanguageProfile('grammar/main.langium').languageId).toBe('langium');
    });

    it('keeps reserved words visibly distinct in the safe generated-code editor', () => {
        const profile = generatedCodeLanguageProfile('src/main.ts');
        const customization = generatedCodeCustomization(profile);
        expect(customization.keywords).toContain('const');
        expect(customization.keywords).toContain('import');
        expect(customization.roleStyles.KEYWORD.color).not.toBe(customization.foreground);
        expect(customization.roleStyles.KEYWORD.bold).toBe(true);
    });
});
