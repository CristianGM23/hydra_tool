import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

describe('grammar status dock information contract', () => {
    it('keeps INFO diagnostics out of Problems and renders them in Information', () => {
        const source=readFileSync(resolve(process.cwd(),'src/grammar-designer/GrammarStatusDock.tsx'),'utf8');
        expect(source).toContain("diagnostics.filter(item => item.severity !== 'INFO')");
        expect(source).toContain("diagnostics.filter(item => item.severity === 'INFO' && item.code !== 'GRAMMAR_EFFECTIVE_ENTRY_RULE')");
        expect(source).toContain('{errors + warnings}');
        expect(source).toContain('informationDiagnostics.map');
        expect(source).toContain("t('Effective entry rule')");
        expect(source).toContain("entryRuleInference.mode === 'MANUAL'");
    });
});
