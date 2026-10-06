import { translateRuntimeUiText, translateUiText, type HydraLanguage } from '../i18n/translationCatalog';

const STORAGE_KEY = 'hydra.language.v1';

/** Reads the active application language without requiring React context. */
export function currentHydraLanguage(): HydraLanguage {
    if (typeof document !== 'undefined') {
        const declared = document.documentElement.lang?.toLowerCase();
        if (declared.startsWith('es')) return 'es';
        if (declared.startsWith('en')) return 'en';
    }
    if (typeof window !== 'undefined') {
        try {
            const stored = window.localStorage.getItem(STORAGE_KEY);
            if (stored === 'es' || stored === 'en') return stored;
        } catch { /* Hardened browsers may disable storage. */ }
    }
    return 'en';
}

export function localizeErrorText(text: string, context = 'operation', code = '', severity = 'ERROR'): string {
    const language = currentHydraLanguage();
    if (language === 'en' || text.trim() === '') return text;
    const known = translateUiText(text, language);
    if (known !== text) return known;
    const remote = translateRemoteDynamic(text);
    if (remote !== null) return remote;
    return translateRuntimeUiText(text, language, context, code, severity);
}


export type ActionableErrorSection = 'reason' | 'solution' | 'check';

/** Keeps structured actionable sections distinct even when backend copy is new. */
export function localizeActionableErrorSection(text: string, section: ActionableErrorSection, code = ''): string {
    const language = currentHydraLanguage();
    if (language === 'en' || text.trim() === '') return text;
    const known = translateUiText(text, language);
    if (known !== text) return known;
    const remote = translateRemoteDynamic(text);
    if (remote !== null) return remote;
    const normalizedCode = code.trim().toUpperCase();
    const remoteFailure = ['REMOTE_ERROR', 'TOKEN_EXCHANGE_FAILED', 'FLOW_EXPIRED', 'PROVIDER_NOT_CONFIGURED', 'CREDENTIALS_MISSING', 'REFRESH_REQUIRED', 'CONNECTION_NOT_FOUND', 'VAULT_UNAVAILABLE'].includes(normalizedCode);
    if (remoteFailure) {
        if (section === 'reason') return 'La respuesta del servicio externo no coincide con un error conocido que HYDRA pueda clasificar con más detalle. El detalle técnico se conserva separado para diagnóstico.';
        if (section === 'solution') return 'Comprueba primero la URL y el destino reconocidos, después el estado de autorización de la cuenta y finalmente Probar conexión. Cambia credenciales o configuración OAuth solo si una comprobación posterior identifica expresamente ese problema.';
        return 'Si el fallo continúa, revisa el detalle técnico y el identificador de correlación junto con el estado del proveedor, red, VPN, proxy y firewall.';
    }
    if (section === 'reason') return 'HYDRA ha recibido un detalle de error nuevo que todavía no dispone de una traducción específica; el detalle técnico original se conserva para identificar la causa.';
    if (section === 'solution') return 'Corrige primero el campo, elemento o configuración indicado por el diagnóstico y repite la operación. Si el problema continúa, consulta el detalle técnico asociado antes de cambiar otras opciones.';
    return 'Conserva el código del error, el campo o elemento indicado y el identificador de correlación para localizar la causa exacta.';
}

export function localizeKnownErrorText(text: string): string {
    return translateUiText(text, currentHydraLanguage());
}

function translateRemoteDynamic(text: string): string | null {
    const provider = '(GitHub|GitLab|Microsoft OneDrive|Google Drive|Dropbox|The external provider)';
    let match = text.match(new RegExp(`^${provider} rejected the current authorization token or session\\.$`));
    if (match !== null) return `${spanishProvider(match[1])} ha rechazado el token o la sesión de autorización actual.`;
    match = text.match(new RegExp(`^${provider} understood the request but the connected account or OAuth token is not allowed to perform it\\.$`));
    if (match !== null) return `${spanishProvider(match[1])} ha entendido la solicitud, pero la cuenta conectada o el token OAuth no tienen permiso para realizarla.`;
    match = text.match(new RegExp(`^${provider} is rate-limiting this connection or its configured quota has been reached\\.$`));
    if (match !== null) return `${spanishProvider(match[1])} está limitando temporalmente esta conexión o se ha alcanzado su cuota configurada.`;
    match = text.match(new RegExp(`^${provider} returned a server-side error while HYDRA was performing the remote operation\\.$`));
    if (match !== null) return `${spanishProvider(match[1])} devolvió un error de servidor mientras HYDRA realizaba la operación remota.`;
    match = text.match(new RegExp(`^${provider} rejected the request because one of the repository coordinates, OAuth parameters or operation values is invalid\\.$`));
    if (match !== null) return `${spanishProvider(match[1])} ha rechazado la solicitud porque alguna coordenada del repositorio, parámetro OAuth o valor de la operación no es válido.`;
    match = text.match(new RegExp(`^${provider} did not respond before the connection timeout expired\\.$`));
    if (match !== null) return `${spanishProvider(match[1])} no respondió antes de que expirara el tiempo de espera de la conexión.`;
    return null;
}
function spanishProvider(value: string): string { return value === 'The external provider' ? 'El proveedor externo' : value; }
