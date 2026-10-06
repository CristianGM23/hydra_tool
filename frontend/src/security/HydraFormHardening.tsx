import { useEffect } from 'react';
import { useHydraLanguage } from '../i18n';

const DEFAULT_TEXT_LIMIT = 4096;
const DEFAULT_TEXTAREA_LIMIT = 262144;
const EXECUTABLE_TEXTAREA_LIMIT = 1048576;
const DEFAULT_URL_LIMIT = 4096;
const ACTIVE_MARKUP = /<\s*(script|iframe|object|embed|svg|math)\b|javascript\s*:|data\s*:\s*text\/html|on[a-z]{3,20}\s*=|\$\{\s*jndi\s*:/i;
const PLAIN_TEXT_SEMANTIC = /(name|title|label|description|message|display|summary|purpose|recommendation|rationale|author|license|obligation)/i;
const EXECUTABLE_SEMANTIC = /(expression|script|code|source|template|query|body|content|payload|blockly|ocl|evl|eol|atl)/i;

function hasForbiddenControlCharacter(value: string): boolean {
    for (const character of value) {
        const code = character.codePointAt(0) ?? 0;
        if ((code >= 0 && code <= 8) || code === 11 || code === 12 || (code >= 14 && code <= 31) || code === 127) return true;
    }
    return false;
}

function supportedTextInput(input: HTMLInputElement): boolean {
    return ['text', 'search', 'email', 'url', 'tel', 'password'].includes(input.type);
}


function semanticName(element: HTMLInputElement | HTMLTextAreaElement): string {
    const label = element.labels?.item(0)?.textContent ?? '';
    return [element.name, element.id, element.getAttribute('aria-label') ?? '', label].join(' ');
}

function isPlainTextControl(element: HTMLInputElement | HTMLTextAreaElement): boolean {
    const semantic = semanticName(element);
    return PLAIN_TEXT_SEMANTIC.test(semantic) && !EXECUTABLE_SEMANTIC.test(semantic);
}

function applyLimit(element: HTMLInputElement | HTMLTextAreaElement): void {
    if (element instanceof HTMLInputElement && !supportedTextInput(element)) return;
    if (element.maxLength >= 0) return;
    if (element instanceof HTMLTextAreaElement) {
        element.maxLength = EXECUTABLE_SEMANTIC.test(semanticName(element)) ? EXECUTABLE_TEXTAREA_LIMIT : DEFAULT_TEXTAREA_LIMIT;
        return;
    }
    element.maxLength = element.type === 'url' ? DEFAULT_URL_LIMIT : DEFAULT_TEXT_LIMIT;
}

/**
 * Global browser-side safety net for current and dynamically mounted forms.
 *
 * Structured designers still own their semantic validation. This bridge adds
 * universal length/control-character checks to raw text controls so a form
 * cannot silently bypass the shared design-system simply because it predates
 * HydraFormField.
 */
export function HydraFormHardening() {
    const { t } = useHydraLanguage();

    useEffect(() => {
        const validate = (element: HTMLInputElement | HTMLTextAreaElement): void => {
            applyLimit(element);
            if (hasForbiddenControlCharacter(element.value)) {
                element.setCustomValidity(t('Control characters are not allowed in this field.'));
                element.dataset.hydraInputRejected = 'true';
                return;
            }
            if (isPlainTextControl(element) && ACTIVE_MARKUP.test(element.value)) {
                element.setCustomValidity(t('Potentially executable markup is not allowed in plain-text fields.'));
                element.dataset.hydraInputRejected = 'true';
                return;
            }
            element.setCustomValidity('');
            delete element.dataset.hydraInputRejected;
        };

        const prepare = (root: ParentNode): void => {
            root.querySelectorAll<HTMLInputElement | HTMLTextAreaElement>('input, textarea').forEach(applyLimit);
        };

        const onInput = (event: Event): void => {
            const target = event.target;
            if (target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement) validate(target);
        };

        prepare(document);
        document.addEventListener('input', onInput, true);
        const observer = new MutationObserver(mutations => {
            for (const mutation of mutations) {
                for (const node of Array.from(mutation.addedNodes)) {
                    if (!(node instanceof Element)) continue;
                    if (node instanceof HTMLInputElement || node instanceof HTMLTextAreaElement) applyLimit(node);
                    prepare(node);
                }
            }
        });
        observer.observe(document.body, { childList: true, subtree: true });
        return () => {
            observer.disconnect();
            document.removeEventListener('input', onInput, true);
        };
    }, [t]);

    return null;
}
