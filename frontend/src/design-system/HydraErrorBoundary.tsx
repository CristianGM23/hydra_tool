import { Component, type ErrorInfo, type ReactNode } from 'react';
import { HydraButton } from './HydraButton';
import { translateRuntimeUiText, translateUiText, type HydraLanguage } from '../i18n/translationCatalog';

interface Props { children: ReactNode; scope: string; onError?: (error: Error, info: ErrorInfo) => void; resetKey?: string; fallback?: ReactNode; }
interface State { error: Error | null; }

/** Evita pantallas en blanco y permite recuperar una vista concreta sin reiniciar HYDRA. */
export class HydraErrorBoundary extends Component<Props, State> {
    state: State = { error: null };

    static getDerivedStateFromError(error: Error): State { return { error }; }

    componentDidCatch(error: Error, info: ErrorInfo): void { this.props.onError?.(error, info); }

    componentDidUpdate(previousProps: Props): void {
        if (this.state.error !== null && previousProps.resetKey !== this.props.resetKey) this.setState({ error: null });
    }

    render(): ReactNode {
        if (this.state.error === null) return this.props.children;
        const language = currentInterfaceLanguage();
        const scope = translateUiText(this.props.scope, language);
        const message = this.state.error.message.trim() === ''
            ? translateUiText('An unexpected interface error occurred.', language)
            : translateRuntimeUiText(this.state.error.message, language, this.props.scope);
        return <section className="hydra-ui-error-boundary" role="alert">
            <strong>{translateUiText('This area could not be rendered', language)}: {scope}</strong>
            <p>{message}</p>
            {this.props.fallback !== undefined && <div className="hydra-ui-error-boundary-fallback">{this.props.fallback}</div>}
            <HydraButton variant="primary" onClick={() => this.setState({ error: null })}>{translateUiText('Try again', language)}</HydraButton>
        </section>;
    }
}

function currentInterfaceLanguage(): HydraLanguage {
    if (typeof document === 'undefined') return 'en';
    return document.documentElement.lang === 'es' ? 'es' : 'en';
}
