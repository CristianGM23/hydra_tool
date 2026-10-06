import { describe, expect, it } from 'vitest';
import { artifactResolutionAdvice, integrationDiagnosticAdvice } from './integrationDiagnosticAdvice';

describe('integration diagnostic advice', () => {
    it('routes a missing event model to Event Designer', () => {
        const advice = integrationDiagnosticAdvice({ severity:'ERROR', code:'UNRESOLVED_REQUIRED_ARTIFACT', elementId:'people-events', path:'artifacts', message:'Event definition model was not found for this metamodel' });
        expect(advice.action).toContain('Event Designer');
        expect(advice.optionalAction).toContain('remove the artifact');
    });
    it('explains how runtime mappings are fixed', () => {
        expect(integrationDiagnosticAdvice({ severity:'WARNING', code:'NO_RUNTIME_MAPPINGS', elementId:'profile', path:'mappings', message:'No mappings' }).action).toContain('Host mappings');
    });
    it('explains unresolved artifact resolution', () => {
        expect(artifactResolutionAdvice({ artifact:{kind:'EVENT_DEFINITIONS',documentId:'people-events',required:true}, status:'NOT_FOUND', message:'Event definition model was not found' }).action).toContain('Event Designer');
    });
});
