import { ActionableApiError, readActionableApiError } from '../api/actionableError';
export class ValidationApiError extends Error {
    readonly status: number;

    constructor(status: number, message: string) {
        super(message);
        this.name = 'ValidationApiError';
        this.status = status;
    }
}

export async function readValidationJson<T>(response: Response): Promise<T> {
    if (!response.ok) {
        const actionable = await readActionableApiError(response);
        throw new ValidationApiError(response.status, actionable.message);
    }
    return response.json() as Promise<T>;
}

export function isMissingValidationResource(error: unknown): boolean {
    return (error instanceof ValidationApiError || error instanceof ActionableApiError) && error.status === 404;
}
