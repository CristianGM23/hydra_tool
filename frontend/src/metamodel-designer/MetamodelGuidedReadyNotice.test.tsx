import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { MetamodelGuidedReadyNotice } from './MetamodelGuidedReadyNotice';

describe('guided metamodel save notice', () => {
    it('does not announce readiness before the fifth step', () => {
        const { container } = render(<MetamodelGuidedReadyNotice currentStepId="validation" ready hasError={false} />);
        expect(container).toBeEmptyDOMElement();
    });

    it('announces readiness only on the save step and only without an active error', () => {
        const { rerender } = render(<MetamodelGuidedReadyNotice currentStepId="save" ready hasError={false} />);
        expect(screen.getByRole('status')).toHaveTextContent('Metamodel ready to finish');
        rerender(<MetamodelGuidedReadyNotice currentStepId="save" ready hasError />);
        expect(screen.getByRole('status')).toHaveTextContent('Metamodel needs review before saving');
        expect(screen.queryByText('Metamodel ready to finish')).not.toBeInTheDocument();
    });
});
