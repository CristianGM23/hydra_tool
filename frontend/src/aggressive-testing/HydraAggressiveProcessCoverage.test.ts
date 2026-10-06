import { describe, expect, it } from 'vitest';
import { HYDRA_MODULES, moduleFromHash, moduleHash } from '../application-shell/moduleRegistry';
import { AGGRESSIVE_PROCESS_SCENARIOS } from './aggressiveProcessCatalog';

describe('aggressive HYDRA process coverage', () => {
    it('has at least one executable acceptance scenario for every registered process', () => {
        const registered = HYDRA_MODULES.map(item => item.id).sort();
        const covered = AGGRESSIVE_PROCESS_SCENARIOS.map(item => item.moduleId).sort();
        expect(covered).toEqual(registered);
        expect(new Set(covered).size).toBe(registered.length);
    });

    it('requires every scenario to prove a final artifact, mutation, recovery or invariant', () => {
        for (const scenario of AGGRESSIVE_PROCESS_SCENARIOS) {
            expect(scenario.process.trim().length).toBeGreaterThan(8);
            expect(scenario.finalEvidence.trim().length).toBeGreaterThan(24);
            expect(scenario.attacks.length).toBeGreaterThanOrEqual(3);
            expect(['ARTIFACT','MUTATION','ROUND_TRIP','RECOVERY','SECURITY','INVARIANT']).toContain(scenario.proof);
        }
    });

    it('round-trips every module through navigation hashes and fails closed for hostile/unknown routes', () => {
        for (const module of HYDRA_MODULES) expect(moduleFromHash(moduleHash(module.id))).toBe(module.id);
        for (const hostile of ['#/../../etc/passwd', '#/%00', '#/not-a-module', '', '#/model<script>']) {
            expect(moduleFromHash(hostile)).toBe('HOME');
        }
    });

    it('keeps module IDs unique under case and separator normalization', () => {
        const normalized = HYDRA_MODULES.map(item => item.id.toLowerCase().replaceAll('_', '-'));
        expect(new Set(normalized).size).toBe(normalized.length);
    });
});
