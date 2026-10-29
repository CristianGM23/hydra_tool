import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { GamificationLedgerView } from './GamificationDesignerParts';
import type { GamificationRuntimeSnapshot } from './types';

/** Estado de ejecución con la forma real del servidor: logros y recompensas viajan como `achievements` y `rewards`. */
function snapshot(actor: Record<string, unknown>): GamificationRuntimeSnapshot {
    return {
        state: { schemaVersion: '1.0', id: 'state', metamodelId: 'people', updatedAt: '2026-10-01T00:00:00Z', processedAwardKeys: [], actors: { 'actor:ana': { actorId: 'actor:ana', points: { 'points:quality': 5 }, ...actor } } },
        stateEtag: 's1',
        ledger: { schemaVersion: '1.0', id: 'ledger', metamodelId: 'people', updatedAt: '2026-10-01T00:00:00Z', entries: [] },
        ledgerEtag: 'l1'
    } as GamificationRuntimeSnapshot;
}

describe('gamification ledger view contract', () => {
    it('counts achievements and rewards using the server field names', () => {
        render(<GamificationLedgerView runtime={snapshot({ levels: {}, achievements: ['achievement:a', 'achievement:b'], challengeProgress: {}, rewards: ['reward:r'] })} feedback={[]} onRefresh={vi.fn()} onReset={vi.fn()} />);
        expect(screen.getByText('actor:ana')).toBeInTheDocument();
        expect(screen.getByText(/2 achievement\(s\) · 1 reward\(s\)/)).toBeInTheDocument();
    });

    it('does not fail when the server omits empty achievements and rewards', () => {
        render(<GamificationLedgerView runtime={snapshot({})} feedback={[]} onRefresh={vi.fn()} onReset={vi.fn()} />);
        expect(screen.getByText(/0 achievement\(s\) · 0 reward\(s\)/)).toBeInTheDocument();
    });
});
