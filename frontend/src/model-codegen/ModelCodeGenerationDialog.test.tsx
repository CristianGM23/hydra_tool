import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { ModelCodeGenerationDialog } from './ModelCodeGenerationDialog';
import type { ModelCodeGenerationTarget } from './types';

const TARGETS = [{ id: 'java', displayName: 'Java', description: 'Java source', extension: '.java', outputKind: 'SOURCE', importCategory: 'Languages', supportedFeatures: ['TESTS'] }] as unknown as ModelCodeGenerationTarget[];

/** Regresión del defecto hallado por el E2E real: cada render del padre reiniciaba la carga de destinos y el selector quedaba vacío. */
describe('code generation dialog target catalog', () => {
    it('keeps the targets when the parent re-renders with new inline callbacks', async () => {
        const load = vi.fn(async () => TARGETS);
        const element = () => <ModelCodeGenerationDialog open modelName="model-1" loadTargets={() => load()} generate={vi.fn(async () => undefined)} onClose={() => undefined} onError={() => undefined} onMessage={() => undefined} />;
        const view = render(element());
        for (let i = 0; i < 6; i += 1) view.rerender(element());
        expect(await screen.findByRole('option', { name: /Java/ })).toBeTruthy();
        expect(load.mock.calls.length).toBeLessThanOrEqual(2);
    });
});
