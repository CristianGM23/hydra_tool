import { useEffect } from 'react';
import { recordAuditActivity } from './api';

interface Props { activeModule: string; }

/**
 * Registra navegación puramente cliente (tabs, guides, properties, palettes,
 * previews...) que nunca atraviesa una API. Deliberadamente no registra todos
 * los clicks: las acciones de negocio las cubre el filtro HTTP/persistencia.
 */
export function ActivityInteractionTracker({ activeModule }: Props) {
    useEffect(() => {
        const listener = (event: MouseEvent) => {
            const target = event.target instanceof Element ? event.target : null;
            const control = target?.closest<HTMLElement>('button,[role="tab"],[role="menuitem"],summary,a[href]');
            if (control === null || control === undefined || ignored(control)) return;
            const candidate = classify(control);
            if (candidate === null) return;
            void recordAuditActivity({
                activityType: candidate.closed ? 'ui.view.closed' : candidate.navigated ? 'navigation.view.changed' : 'ui.view.opened',
                moduleId: moduleId(activeModule), action: candidate.closed ? 'CLOSE' : candidate.navigated ? 'NAVIGATE' : 'OPEN',
                resourceType: 'interface-view', resourceId: candidate.id, displayName: candidate.label,
                metadata: { controlRole: candidate.role, activeModule }, dedupeKey: candidate.id
            });
        };
        document.addEventListener('click', listener, true);
        return () => document.removeEventListener('click', listener, true);
    }, [activeModule]);
    return null;
}

function classify(control: HTMLElement): { id: string; label: string; role: string; navigated: boolean; closed: boolean } | null {
    const role = control.getAttribute('role') ?? control.tagName.toLowerCase();
    const label = clean(control.getAttribute('aria-label') ?? control.getAttribute('title') ?? control.textContent ?? '');
    if (label === '') return null;
    const className = typeof control.className === 'string' ? control.className.toLowerCase() : '';
    const text = label.toLowerCase();
    const inProjectTabs = control.closest('.project-tabs') !== null;
    const navigated = role === 'tab' || inProjectTabs || className.includes('tab') || className.includes('section-link');
    const closed = /^(close|cerrar)\b/.test(text) || className.includes('close');
    const viewCommand = navigated || closed || className.includes('guide') || className.includes('help') || className.includes('preview')
        || className.includes('properties') || className.includes('structure') || className.includes('palette') || className.includes('explorer')
        || /\b(guide|help|preview|properties|structure|palette|explorer|guía|ayuda|propiedades|estructura|paleta|explorador)\b/.test(text);
    if (!viewCommand) return null;
    const stable = control.id || control.getAttribute('aria-controls') || control.getAttribute('data-testid') || slug(label);
    return { id: stable.slice(0, 160), label: label.slice(0, 512), role, navigated, closed };
}

function ignored(control: HTMLElement): boolean {
    return control.closest('.hydra-activity-log,[data-hydra-audit-ignore="true"],.hydra-module-rail,.hydra-home-module-row,.hydra-brand-button') !== null;
}

function moduleId(value: string): string {
    const normalized = value.toLowerCase().replace(/[^a-z0-9._-]+/g, '-').replace(/^-+|-+$/g, '');
    return normalized === '' ? 'general' : normalized;
}
function slug(value: string): string {
    const normalized = value.toLowerCase().replace(/[^a-z0-9áéíóúüñ._-]+/gi, '-').replace(/^-+|-+$/g, '');
    return normalized === '' ? 'interface-view' : normalized;
}
function clean(value: string): string { return value.replace(/\s+/g, ' ').trim(); }
