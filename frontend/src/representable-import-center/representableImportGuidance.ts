import type { MetaClass, MetaModel } from '../metamodel-designer/types';
import type { ExternalChangeCandidate, Representability, RepresentableImportDiagnostic, RepresentableImportPreview } from './types';

export type CandidateResolution = 'CREATE_NEW' | `EXISTING:${string}`;
export type CandidateResolutionMap = Partial<Record<string, CandidateResolution>>;

export interface ExternalChangeAnalysis {
    valid: boolean;
    changes: ExternalChangeCandidate[];
    error: string;
}

const CLASS_NATIVE_KINDS = new Set(['ECLASS', 'EINTERFACE', 'CLASS', 'INTERFACE', 'ENTITY']);

export function parseExternalChanges(source: string): ExternalChangeAnalysis {
    try {
        const parsed: unknown = JSON.parse(source);
        if (!Array.isArray(parsed)) return { valid: false, changes: [], error: 'The external change document must be a JSON array.' };
        const changes: ExternalChangeCandidate[] = [];
        for (let index = 0; index < parsed.length; index += 1) {
            const item = parsed[index];
            if (item === null || typeof item !== 'object' || Array.isArray(item)) return { valid: false, changes: [], error: `Candidate ${index + 1} must be a JSON object.` };
            const candidate = item as Partial<ExternalChangeCandidate>;
            if (typeof candidate.externalId !== 'string' || candidate.externalId.trim() === '') return { valid: false, changes: [], error: `Candidate ${index + 1} is missing externalId.` };
            if (!['ADD', 'UPDATE', 'DELETE', 'MOVE', 'RENAME'].includes(String(candidate.changeKind))) return { valid: false, changes: [], error: `Candidate ${index + 1} has an unsupported changeKind.` };
            if (typeof candidate.nativeKind !== 'string' || candidate.nativeKind.trim() === '') return { valid: false, changes: [], error: `Candidate ${index + 1} is missing nativeKind.` };
            if (typeof candidate.qualifiedName !== 'string' || candidate.qualifiedName.trim() === '') return { valid: false, changes: [], error: `Candidate ${index + 1} is missing qualifiedName.` };
            changes.push({
                externalId: candidate.externalId,
                changeKind: candidate.changeKind as ExternalChangeCandidate['changeKind'],
                nativeKind: candidate.nativeKind,
                qualifiedName: candidate.qualifiedName,
                properties: candidate.properties !== null && typeof candidate.properties === 'object' && !Array.isArray(candidate.properties) ? candidate.properties as Record<string, unknown> : {}
            });
        }
        return { valid: true, changes, error: '' };
    } catch (error) {
        return { valid: false, changes: [], error: error instanceof Error ? `The JSON cannot be read: ${error.message}` : 'The JSON cannot be read.' };
    }
}

export function isClassLikeCandidate(candidate: ExternalChangeCandidate): boolean {
    return CLASS_NATIVE_KINDS.has(candidate.nativeKind.trim().toUpperCase());
}

export function shortExternalName(candidate: ExternalChangeCandidate): string {
    const tokens = candidate.qualifiedName.split(/[.#:/\\]/).filter(Boolean);
    return tokens.at(-1) ?? candidate.qualifiedName;
}

function kindCompatible(candidate: ExternalChangeCandidate, metaClass: MetaClass): boolean {
    const native = candidate.nativeKind.trim().toUpperCase();
    if (native === 'EINTERFACE' || native === 'INTERFACE') return metaClass.kind === 'INTERFACE';
    return metaClass.kind !== 'INTERFACE';
}

export function matchingClasses(candidate: ExternalChangeCandidate, metamodel: MetaModel | null): MetaClass[] {
    if (metamodel === null || !isClassLikeCandidate(candidate)) return [];
    const wanted = shortExternalName(candidate).toLowerCase();
    return metamodel.classes
        .filter(metaClass => kindCompatible(candidate, metaClass))
        .sort((left, right) => {
            const leftExact = left.name.toLowerCase() === wanted ? 0 : 1;
            const rightExact = right.name.toLowerCase() === wanted ? 0 : 1;
            return leftExact - rightExact || left.name.localeCompare(right.name);
        });
}

export function recommendedResolution(candidate: ExternalChangeCandidate, metamodel: MetaModel | null): CandidateResolution | undefined {
    if (!isClassLikeCandidate(candidate)) return undefined;
    const exact = matchingClasses(candidate, metamodel).find(metaClass => metaClass.name.toLowerCase() === shortExternalName(candidate).toLowerCase());
    if (exact !== undefined) return `EXISTING:${exact.id}`;
    return candidate.changeKind === 'ADD' ? 'CREATE_NEW' : undefined;
}

export function decorateChanges(changes: ExternalChangeCandidate[], resolutions: CandidateResolutionMap): ExternalChangeCandidate[] {
    return changes.map(candidate => {
        const resolution = resolutions[candidate.externalId];
        if (resolution === undefined || !isClassLikeCandidate(candidate)) return candidate;
        const properties = { ...candidate.properties };
        if (resolution === 'CREATE_NEW') {
            properties['hydra:resolution'] = 'CREATE_NEW';
            delete properties['hydra:targetElementId'];
        } else {
            properties['hydra:resolution'] = 'MAP_EXISTING';
            properties['hydra:targetElementId'] = resolution.slice('EXISTING:'.length);
        }
        return { ...candidate, properties };
    });
}

export function resolutionComplete(changes: ExternalChangeCandidate[], resolutions: CandidateResolutionMap): boolean {
    return changes.filter(isClassLikeCandidate).every(candidate => resolutions[candidate.externalId] !== undefined);
}

export function previewReviewState(preview: RepresentableImportPreview | null): { label: string; tone: 'ready' | 'review' | 'blocked' | 'empty'; issueCount: number; reviewCount: number } {
    if (preview === null) return { label: 'No preview', tone: 'empty', issueCount: 0, reviewCount: 0 };
    const blocked = preview.diagnostics.filter(item => item.representability === 'NONE').length;
    const partial = preview.diagnostics.filter(item => item.representability === 'PARTIAL').length;
    if (!preview.applicable || blocked > 0) return { label: 'Blocked', tone: 'blocked', issueCount: blocked, reviewCount: partial };
    if (partial > 0) return { label: 'Review required', tone: 'review', issueCount: 0, reviewCount: partial };
    return { label: 'Ready', tone: 'ready', issueCount: 0, reviewCount: 0 };
}

export function diagnosticLevel(representability: Representability): 'success' | 'warning' | 'error' {
    return representability === 'FULL' ? 'success' : representability === 'PARTIAL' ? 'warning' : 'error';
}

export function diagnosticStatusLabel(representability: Representability): string {
    return representability === 'FULL' ? 'Compatible' : representability === 'PARTIAL' ? 'Needs review' : 'Blocked';
}

export function diagnosticExplanation(diagnostic: RepresentableImportDiagnostic, candidate: ExternalChangeCandidate | undefined): { title: string; affected: string; explanation: string; action: string } {
    const affected = candidate?.qualifiedName?.trim() || diagnostic.externalId;
    if (diagnostic.representability === 'FULL') return {
        title: 'Compatible change', affected,
        explanation: 'HYDRA can represent the selected external element completely with the neutral model. No semantic loss has been detected.',
        action: 'Review the proposed destination and incoming properties. No corrective action is required when they are correct.'
    };
    if (diagnostic.code === 'TARGET_ELEMENT_MISSING') return {
        title: 'Existing destination is missing', affected,
        explanation: 'This change was configured to reuse an existing HYDRA element, but no destination class is selected.',
        action: 'Return to the change review, choose the existing class that represents this concept, and generate the preview again.'
    };
    if (diagnostic.code === 'NATIVE_KIND_UNSUPPORTED') return {
        title: 'Unsupported external element type', affected,
        explanation: 'The selected technology adapter does not know how to convert this element type into a neutral HYDRA element without losing its meaning.',
        action: 'Keep this construct in its native technology, exclude it from the package, or install/develop a HYDRA mapper that supports this element type.'
    };
    if (diagnostic.representability === 'PARTIAL') return {
        title: 'Review before importing', affected,
        explanation: 'HYDRA can represent the selected external element, but part of its technology-specific information does not have an equivalent neutral contract.',
        action: 'Compare the proposed neutral values with the source. If the omitted semantics matter, keep the construct native or provide a compatible HYDRA mapper before persisting.'
    };
    return {
        title: 'Cannot import automatically', affected,
        explanation: 'HYDRA does not currently have a safe neutral representation for the selected external element. Persisting it automatically could lose or misinterpret information.',
        action: 'Do not force the import. Keep the construct in its native technology, remove it from this package, or add a HYDRA mapper that defines how it must be represented.'
    };
}
