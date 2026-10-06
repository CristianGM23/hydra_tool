import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { HydraCreationModeSwitch, HydraGuidedWorkflow, type HydraGuidedStep } from './HydraGuidedWorkflow';
import { isGuidedStepReady, isGuidedStepUnlocked } from './HydraGuidedWorkflowModel';
import { HydraLanguageProvider } from '../i18n/HydraLanguageContext';

const steps = (firstComplete: boolean): HydraGuidedStep[] => [
    { id: 'one', title: 'First', description: 'First block', checks: [{ id: 'required', label: 'Required value', complete: firstComplete }] },
    { id: 'two', title: 'Second', description: 'Second block', checks: [{ id: 'second', label: 'Second value', complete: true }] }
];

describe('HydraGuidedWorkflow', () => {
    it('shows invalid current steps in red and keeps later steps pending/locked', () => {
        const model = steps(false);
        expect(isGuidedStepReady(model[0])).toBe(false);
        expect(isGuidedStepUnlocked(model, 0)).toBe(true);
        expect(isGuidedStepUnlocked(model, 1)).toBe(false);

        render(<HydraGuidedWorkflow title="Guide" description="Guide description" steps={model} currentStepId="one" onStepChange={() => undefined} />);
        expect(screen.getByRole('button', { name: /2\. Second/ })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Next step' })).toBeDisabled();
        expect(screen.getByLabelText('Step incomplete')).toBeInTheDocument();
        expect(screen.getByLabelText('Step pending')).toBeInTheDocument();
    });

    it('does not mark a pre-populated valid step green until the user explicitly confirms it', () => {
        const onStepChange = vi.fn();
        const model = steps(true);
        render(<HydraGuidedWorkflow title="Guide" description="Guide description" steps={model} currentStepId="one" onStepChange={onStepChange} />);

        expect(screen.queryByLabelText('Step complete')).not.toBeInTheDocument();
        expect(screen.getByLabelText('Step ready for confirmation')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /2\. Second/ })).toBeDisabled();
        fireEvent.click(screen.getByRole('button', { name: 'Next step' }));
        expect(onStepChange).toHaveBeenCalledWith('two');
    });

    it('translates guided step titles, descriptions, checks and details centrally in Spanish', () => {
        window.localStorage.setItem('hydra.language.v1', 'es');
        const spanishSteps: HydraGuidedStep[] = [{
            id: 'catalog',
            title: 'Gamification catalog',
            description: 'Name the configuration and establish its point scales.',
            checks: [{ id: 'name', label: 'Gamification document has a name', complete: true, detail: 'This identifies the optional gamification configuration in the project.' }]
        }];
        render(<HydraLanguageProvider><HydraGuidedWorkflow title="Guided gamification" description="Review" steps={spanishSteps} currentStepId="catalog" onStepChange={() => undefined} /></HydraLanguageProvider>);
        expect(screen.getAllByText('Catálogo de gamificación').length).toBeGreaterThan(0);
        expect(screen.getAllByText('Asigna un nombre a la configuración y define sus escalas de puntos.').length).toBeGreaterThan(0);
        expect(screen.getByText('El documento de gamificación tiene nombre')).toBeInTheDocument();
        expect(screen.getByText('Esto identifica la configuración opcional de gamificación dentro del proyecto.')).toBeInTheDocument();
        window.localStorage.removeItem('hydra.language.v1');
    });

    it('does not mark the final step complete when the finish action reports failure', async () => {
        const singleStep: HydraGuidedStep[] = [{ id: 'only', title: 'Only', description: 'Only block', checks: [{ id: 'ready', label: 'Ready', complete: true }] }];
        const onFinish = vi.fn().mockResolvedValue(false);
        render(<HydraGuidedWorkflow title="Guide" description="Guide description" steps={singleStep} currentStepId="only" onStepChange={() => undefined} onFinish={onFinish} finishLabel="Save" />);
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));
        expect(await screen.findByText('0/1 complete')).toBeInTheDocument();
        expect(onFinish).toHaveBeenCalledTimes(1);
    });


    it('never lets optional steps bypass an actual blocking error', () => {
        const model: HydraGuidedStep[] = [{
            id: 'optional-but-invalid', title: 'Optional block', description: 'Optional content', optional: true,
            checks: [{ id: 'broken', label: 'Broken required field', complete: false }]
        }];
        expect(isGuidedStepReady(model[0])).toBe(false);
        render(<HydraGuidedWorkflow title="Guide" description="Guide description" steps={model} currentStepId="optional-but-invalid" onStepChange={() => undefined} onFinish={() => true} finishLabel="Save" />);
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    });

    it('keeps later progress blocked when an already confirmed previous step becomes invalid', () => {
        const onStepChange = vi.fn();
        const first = steps(true);
        const { rerender } = render(<HydraGuidedWorkflow title="Guide" description="Guide description" steps={first} currentStepId="one" onStepChange={onStepChange} />);
        fireEvent.click(screen.getByRole('button', { name: 'Next step' }));
        rerender(<HydraGuidedWorkflow title="Guide" description="Guide description" steps={steps(false)} currentStepId="two" onStepChange={onStepChange} onFinish={() => true} />);
        expect(screen.getByRole('button', { name: /2\. Second/ })).toBeEnabled();
        expect(screen.getByRole('button', { name: 'Finish' })).toBeDisabled();
        expect(screen.getByText(/An earlier step is no longer valid/)).toBeInTheDocument();
    });

    it('allows explicitly non-blocking informational checks to stay incomplete', () => {
        const model: HydraGuidedStep[] = [{
            id: 'review', title: 'Review', description: 'Review block',
            checks: [{ id: 'info', label: 'Optional recommendation', complete: false, blocking: false }]
        }];
        expect(isGuidedStepReady(model[0])).toBe(true);
    });

    it('keeps guided and advanced/manual modes explicitly available', () => {
        const onChange = vi.fn();
        render(<HydraCreationModeSwitch mode="GUIDED" onChange={onChange} />);
        fireEvent.click(screen.getByRole('button', { name: 'Advanced / manual' }));
        expect(onChange).toHaveBeenCalledWith('ADVANCED');
    });
});
