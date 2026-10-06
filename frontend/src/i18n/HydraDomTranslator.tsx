import { useLayoutEffect } from 'react';
import { useHydraLanguage } from './useHydraLanguage';
import { translateRuntimeUiText, translateUiText } from './translationCatalog';

type TextRecord = { source: string; applied: string };
type AttributeRecord = { source: string; applied: string };

const textRecords = new WeakMap<Text, TextRecord>();
const attributeRecords = new WeakMap<Element, Map<string, AttributeRecord>>();
const TRANSLATED_ATTRIBUTES = ['aria-label', 'aria-description', 'title', 'placeholder', 'alt', 'data-tooltip', 'data-help', 'data-label'] as const;
const SKIP_SELECTOR = 'script,style,code,pre,kbd,samp,[contenteditable="true"],[data-hydra-i18n-skip="true"],[data-hydra-i18n-explicit="true"],[data-hydra-technical="true"],.hydra-id-preview,.project-active-path,.monaco-editor';

/**
 * Compatibility bridge for the existing application. It translates only visible
 * DOM text and accessibility attributes. It never changes control values, IDs,
 * request payloads, persisted documents, enum values, paths or source code.
 */
export function HydraDomTranslator() {
    const { language } = useHydraLanguage();

    useLayoutEffect(() => {
        let applying = false;
        let translatedTextNodes = 0;
        let translatedAttributes = 0;
        const startedAt = performance.now();
        // Observe the body, not only #root: HYDRA renders dialogs, tooltips and dropdowns
        // through React portals attached to document.body. Limiting observation to #root
        // left those overlays in English after switching the application to Spanish.
        const root = document.body;

        function applyNode(node: Node): void {
            if (node.nodeType === Node.TEXT_NODE) {
                applyText(node as Text);
                return;
            }
            if (!(node instanceof Element) || shouldSkip(node)) return;
            applyAttributes(node);
            for (const child of Array.from(node.childNodes)) applyNode(child);
        }

        function applyText(node: Text): void {
            const parent = node.parentElement;
            if (parent === null || shouldSkip(parent)) return;
            const current = node.nodeValue ?? '';
            if (!isEligibleText(parent, current)) return;
            let record = textRecords.get(node);
            if (record === undefined) {
                record = { source: current, applied: current };
                textRecords.set(node, record);
            } else if (current !== record.applied && !applying) {
                const expected = translateForElement(parent, record.source, language);
                if (current === expected) record.applied = current;
                else record = { source: current, applied: current };
                textRecords.set(node, record);
            }
            const next = translateForElement(parent, record.source, language);
            if (current === next) { record.applied = next; return; }
            const runtimeHost = parent.closest<HTMLElement>('[data-hydra-runtime-message="true"]');
            if (language === 'es' && runtimeHost !== null && record.source.trim() !== '' && next !== record.source) runtimeHost.dataset.hydraTechnicalDetail = record.source;
            applying = true;
            node.nodeValue = next;
            record.applied = next;
            applying = false;
            translatedTextNodes += 1;
        }

        function applyAttributes(element: Element): void {
            let records = attributeRecords.get(element);
            if (records === undefined) {
                records = new Map();
                attributeRecords.set(element, records);
            }
            const attributes: string[] = [...TRANSLATED_ATTRIBUTES];
            if ((element instanceof HTMLInputElement && ['button', 'submit', 'reset'].includes(element.type))
                    || element instanceof HTMLOptionElement || element instanceof HTMLOptGroupElement) attributes.push('label');
            if (element instanceof HTMLInputElement && ['button', 'submit', 'reset'].includes(element.type)) attributes.push('value');
            for (const attribute of attributes) {
                const current = element.getAttribute(attribute);
                if (current === null || current.trim() === '') continue;
                let record = records.get(attribute);
                if (record === undefined) {
                    record = { source: current, applied: current };
                    records.set(attribute, record);
                } else if (current !== record.applied && !applying) {
                    const expected = translateUiText(record.source, language);
                    if (current === expected) record.applied = current;
                    else record = { source: current, applied: current };
                    records.set(attribute, record);
                }
                const next = translateUiText(record.source, language);
                if (current === next) { record.applied = next; continue; }
                applying = true;
                element.setAttribute(attribute, next);
                record.applied = next;
                applying = false;
                translatedAttributes += 1;
            }
        }

        applyNode(root);
        const queuedNodes = new Set<Node>();
        const queuedElements = new Set<Element>();
        let frame = 0;
        const flush = () => {
            frame = 0;
            if (applying) return;
            const roots = Array.from(queuedNodes).filter(node => {
                for (let parent = node.parentNode; parent !== null; parent = parent.parentNode) if (queuedNodes.has(parent)) return false;
                return true;
            });
            for (const node of roots) applyNode(node);
            for (const element of queuedElements) {
                let coveredByQueuedRoot = false;
                for (let current: Node | null = element; current !== null; current = current.parentNode) {
                    if (queuedNodes.has(current)) { coveredByQueuedRoot = true; break; }
                }
                if (!coveredByQueuedRoot) applyAttributes(element);
            }
            queuedNodes.clear(); queuedElements.clear();
        };
        const observer = new MutationObserver(mutations => {
            if (applying) return;
            for (const mutation of mutations) {
                if (mutation.type === 'characterData') queuedNodes.add(mutation.target);
                if (mutation.type === 'childList') mutation.addedNodes.forEach(node => queuedNodes.add(node));
                if (mutation.type === 'attributes' && mutation.target instanceof Element) queuedElements.add(mutation.target);
            }
            if (frame === 0) frame = requestAnimationFrame(flush);
        });
        observer.observe(root, { subtree: true, childList: true, characterData: true, attributes: true, attributeFilter: [...TRANSLATED_ATTRIBUTES, 'label', 'value'] });
        const detail = { language, translatedTextNodes, translatedAttributes, durationMillis: performance.now() - startedAt };
        window.dispatchEvent(new CustomEvent('hydra:i18n-dom-translation', { detail }));
        return () => { observer.disconnect(); if (frame !== 0) cancelAnimationFrame(frame); };
    }, [language]);

    return null;
}

function translateForElement(parent: Element, source: string, language: 'en' | 'es'): string {
    const runtimeHost = parent.closest<HTMLElement>('[data-hydra-runtime-message="true"]');
    if (runtimeHost === null) return translateUiText(source, language);
    return translateRuntimeUiText(source, language, runtimeHost.dataset.hydraMessageContext ?? 'operation', runtimeHost.dataset.hydraMessageCode ?? '', runtimeHost.dataset.hydraMessageSeverity ?? '');
}

function isEligibleText(_parent: Element, value: string): boolean {
    // Every non-empty visible node may be checked now that the catalogue has no
    // general word-by-word fallback. Unknown names stay byte-for-byte unchanged,
    // while known one-word status labels inside spans (Saved, Active, Runtime…)
    // are no longer left in English.
    return value.trim() !== '';
}

function shouldSkip(element: Element): boolean {
    return element.matches(SKIP_SELECTOR) || element.closest(SKIP_SELECTOR) !== null;
}
