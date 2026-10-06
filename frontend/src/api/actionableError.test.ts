import { describe, expect, it } from 'vitest';
import { ActionableApiError, readActionableApiError } from './actionableError';

describe('actionable API errors', () => {
    it('explains the field, reason and solution returned by HYDRA', async () => {
        const response = new Response(JSON.stringify({ code:'INVALID_JSON', message:'The value was rejected.', field:'actionModel.id', reason:'Spaces are not allowed.', solution:'Use action-model.', suggestions:['Keep the ID stable.'] }), { status:400, headers:{'Content-Type':'application/json'} });
        const error = await readActionableApiError(response);
        expect(error).toBeInstanceOf(ActionableApiError);
        expect(error.message).toContain('What went wrong:');
        expect(error.message).toContain('Field: Id');
        expect(error.message).toContain('Why it is invalid: Spaces are not allowed.');
        expect(error.message).toContain('How to fix it: Use action-model.');
    });

    it('keeps a useful fallback for non-JSON responses', async () => {
        const error = await readActionableApiError(new Response('Gateway failed', { status:502 }));
        expect(error.message).toContain('The external service could not complete the requested operation.');
        expect(error.technicalDetail).toBe('Gateway failed');
    });


    it('falls back safely when the response body was already consumed', async () => {
        const response = new Response('Already consumed', { status:400 });
        await response.text();
        const error = await readActionableApiError(response);
        expect(error).toBeInstanceOf(ActionableApiError);
        expect(error.message).toContain('The submitted data is not valid.');
    });
    it('preserves impact, safe recovery and correlation evidence from problem+json', async () => {
        const response = new Response(JSON.stringify({ code:'CONFLICT', detail:'State changed.', reason:'The resource changed.', solution:'Reload it.', impact:'The write was not applied.', retryable:true, correlationId:'corr-123', operation:'PUT /api/items/1', affectedResource:'/api/items/1', recoveryActions:[{id:'refresh',label:'Reload the current state.',kind:'REFRESH',safe:true}], technicalDetail:'etag mismatch' }), { status:409, headers:{'Content-Type':'application/problem+json'} });
        const error = await readActionableApiError(response);
        expect(error.retryable).toBe(true);
        expect(error.correlationId).toBe('corr-123');
        expect(error.recoveryActions).toEqual([{id:'refresh',label:'Reload the current state.',kind:'REFRESH',safe:true}]);
        expect(error.message).toContain('Impact: The write was not applied.');
        expect(error.message).toContain('Recovery: [safe] Reload the current state.');
        expect(error.message).toContain('Correlation ID: corr-123');
        expect(error.message).toContain('Technical details: etag mismatch');
    });

});
