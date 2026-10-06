import { describe, expect, it } from 'vitest';
import { evaluateBundle } from './check-bundle-budget.mjs';

const budget = {
    entryMaxBytes: 100,
    asyncChunkMaxBytes: 60,
    entryMaxGzipBytes: 50,
    asyncChunkMaxGzipBytes: 30,
    totalApplicationJavaScriptMaxBytes: 180,
    totalJavaScriptMaxBytes: 180,
    moduleBudgets: {},
    exemptions: []
};

describe('HYDRA bundle budget', () => {
    it('accepts entry and async chunks below configurable limits', () => {
        const result = evaluateBundle([
            { file: 'entry.js', isEntry: true, bytes: 90, gzipBytes: 40 },
            { file: 'module.js', isEntry: false, bytes: 50, gzipBytes: 20 }
        ], budget);

        expect(result.violations).toEqual([]);
        expect(result.totalJavaScriptBytes).toBe(140);
        expect(result.totalApplicationJavaScriptBytes).toBe(140);
    });

    it('reports raw, gzip, physical-total and application-total regressions independently', () => {
        const result = evaluateBundle([
            { file: 'entry.js', isEntry: true, bytes: 120, gzipBytes: 55 },
            { file: 'module.js', isEntry: false, bytes: 70, gzipBytes: 35 }
        ], budget);

        expect(result.violations).toHaveLength(6);
        expect(result.violations.filter(value => value.startsWith('Total JavaScript'))).toHaveLength(1);
        expect(result.violations.filter(value => value.startsWith('Application JavaScript'))).toHaveLength(1);
    });

    it('measures matching lazy chunks against module-specific budgets', () => {
        const result = evaluateBundle([
            { file: 'assets/UpdateSiteImportCenter-abc.js', isEntry: false, bytes: 70, gzipBytes: 32 }
        ], {
            ...budget,
            totalJavaScriptMaxBytes: 200,
            moduleBudgets: {
                'update-site-import-center': {
                    patterns: ['UpdateSiteImportCenter'],
                    maxBytes: 65,
                    maxGzipBytes: 30,
                    required: true
                }
            }
        });

        expect(result.modules['update-site-import-center'].bytes).toBe(70);
        expect(result.violations.filter(value => value.includes('Module update-site-import-center'))).toHaveLength(2);
    });

    it('keeps heavyweight vendor chunks under explicit budgets without charging them to the application total', () => {
        const result = evaluateBundle([
            { file: 'assets/monaco-abc.js', isEntry: false, bytes: 140, gzipBytes: 45 },
            { file: 'assets/app.js', isEntry: false, bytes: 50, gzipBytes: 20 }
        ], {
            ...budget,
            totalJavaScriptMaxBytes: 220,
            totalApplicationJavaScriptMaxBytes: 60,
            moduleBudgets: {
                monaco: {
                    patterns: ['monaco-'], maxBytes: 150, maxGzipBytes: 50, required: true,
                    excludeFromGenericChunkBudget: true, excludeFromApplicationTotal: true
                }
            }
        });
        expect(result.violations).toEqual([]);
        expect(result.totalJavaScriptBytes).toBe(190);
        expect(result.totalApplicationJavaScriptBytes).toBe(50);
    });
});
