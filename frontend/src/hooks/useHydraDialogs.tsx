import { useCallback, useRef, useState, type ReactNode } from 'react';
import { HydraButton, HydraFormField, HydraModal } from '../design-system';

export interface HydraDecisionRequest {
    title: string;
    description: string;
    confirmLabel: string;
    cancelLabel: string;
    danger?: boolean;
}

export interface HydraTextRequest {
    title: string;
    description: string;
    label: string;
    initialValue?: string;
    confirmLabel: string;
    cancelLabel: string;
}

/**
 * Gestiona confirmaciones y entradas de texto con modales HYDRA sin bloquear el navegador.
 *
 * @returns funciones asíncronas para solicitar una decisión o un texto y el nodo visual que debe renderizarse una vez en el componente consumidor.
 */
export function useHydraDialogs(): {
    requestDecision: (request: HydraDecisionRequest) => Promise<boolean>;
    requestText: (request: HydraTextRequest) => Promise<string | null>;
    dialogs: ReactNode;
} {
    const [decision, setDecision] = useState<HydraDecisionRequest | null>(null);
    const [textRequest, setTextRequest] = useState<HydraTextRequest | null>(null);
    const [textValue, setTextValue] = useState('');
    const decisionResolver = useRef<((accepted: boolean) => void) | null>(null);
    const textResolver = useRef<((value: string | null) => void) | null>(null);

    /**
     * Abre una confirmación no bloqueante y devuelve la decisión del usuario.
     *
     * @param request describe título, explicación y etiquetas de las acciones.
     * @returns true cuando se confirma y false cuando se cancela.
     */
    const requestDecision = useCallback((request: HydraDecisionRequest): Promise<boolean> => new Promise(resolve => {
        decisionResolver.current?.(false);
        decisionResolver.current = resolve;
        setDecision(request);
    }), []);

    /**
     * Abre una entrada de texto no bloqueante y conserva el valor inicial indicado.
     *
     * @param request describe el texto solicitado y las etiquetas de las acciones.
     * @returns el texto confirmado o null cuando se cancela.
     */
    const requestText = useCallback((request: HydraTextRequest): Promise<string | null> => new Promise(resolve => {
        textResolver.current?.(null);
        textResolver.current = resolve;
        setTextValue(request.initialValue ?? '');
        setTextRequest(request);
    }), []);

    /**
     * Resuelve la confirmación activa y libera su promesa pendiente.
     *
     * @param accepted indica si el usuario confirmó la operación.
     * @returns no devuelve ningún valor.
     */
    function settleDecision(accepted: boolean): void {
        const resolve = decisionResolver.current;
        decisionResolver.current = null;
        setDecision(null);
        resolve?.(accepted);
    }

    /**
     * Resuelve la entrada de texto activa con el valor actual o con cancelación.
     *
     * @param accepted indica si debe devolverse el texto actual.
     * @returns no devuelve ningún valor.
     */
    function settleText(accepted: boolean): void {
        const resolve = textResolver.current;
        textResolver.current = null;
        const result = accepted ? textValue : null;
        setTextRequest(null);
        resolve?.(result);
    }

    const dialogs = <>
        {decision !== null && <HydraModal
            open
            title={decision.title}
            description={decision.description}
            onClose={() => settleDecision(false)}
            footer={<><HydraButton variant="secondary" onClick={() => settleDecision(false)}>{decision.cancelLabel}</HydraButton><HydraButton variant={decision.danger ? 'danger' : 'primary'} onClick={() => settleDecision(true)}>{decision.confirmLabel}</HydraButton></>}
        >{null}</HydraModal>}
        {textRequest !== null && <HydraModal
            open
            title={textRequest.title}
            description={textRequest.description}
            onClose={() => settleText(false)}
            footer={<><HydraButton variant="secondary" onClick={() => settleText(false)}>{textRequest.cancelLabel}</HydraButton><HydraButton variant="primary" disabled={textValue.trim() === ''} onClick={() => settleText(true)}>{textRequest.confirmLabel}</HydraButton></>}
        ><HydraFormField label={textRequest.label} targetId="hydra-dialog-text"><input id="hydra-dialog-text" autoFocus value={textValue} onChange={event => setTextValue(event.target.value)} /></HydraFormField></HydraModal>}
    </>;

    return { requestDecision, requestText, dialogs };
}
