import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { UpdateSiteImportDashboard } from './UpdateSiteImportDashboard';
import type { UpdateSiteImportDashboard as Dashboard } from './types';

const dashboard: Dashboard = {
    inspectedFiles: 12, recoveredFiles: 5, partialFiles: 2, supportingFiles: 3,
    unrecoverableFiles: 2, failedFiles: 0, recoveryCoverage: 68,
    confidence: [
        { id: 'EXACT', label: 'Exact', value: 6, explanation: 'Original metamodel evidence.' },
        { id: 'RECOVERED', label: 'Recovered', value: 4, explanation: 'Generated artifacts.' }
    ],
    fileStatus: [
        { id: 'RECOVERED', label: 'Recovered', value: 5, explanation: 'Converted.' },
        { id: 'PARTIAL', label: 'Partial', value: 2, explanation: 'Incomplete.' },
        { id: 'SUPPORTING', label: 'Supporting', value: 3, explanation: 'Supporting evidence.' },
        { id: 'NOT_RECOVERABLE', label: 'Not recoverable', value: 2, explanation: 'Explained only.' }
    ],
    bundleRoles: [{ id: 'MODEL_PLUGIN', label: 'Model plug-in', value: 2, explanation: 'Contains Ecore.' }],
    stages: [],
    flowNodes: [],
    flowLinks: []
};

describe('UpdateSiteImportDashboard', () => {
    it('renders compact KPIs and circular coverage, outcome, confidence and role charts', () => {
        render(<UpdateSiteImportDashboard dashboard={dashboard} />);

        expect(screen.getByRole('region', { name: 'Import recovery dashboard' })).toBeInTheDocument();
        expect(screen.getByText('68%')).toBeInTheDocument();
        expect(screen.getByRole('img', { name: /recovery coverage 68 percent/i })).toBeInTheDocument();
        expect(screen.getByRole('img', { name: /file outcome distribution/i })).toBeInTheDocument();
        expect(screen.getByRole('img', { name: /confidence distribution/i })).toBeInTheDocument();
        expect(screen.getByRole('img', { name: /bundle role distribution/i })).toBeInTheDocument();
        expect(screen.getByText('Model plug-in')).toBeInTheDocument();
        expect(screen.queryByText('Recovery flow')).not.toBeInTheDocument();
        expect(screen.getAllByRole('button', { name: /about/i }).length).toBeGreaterThanOrEqual(8);
    });
});
