// Ayudantes compartidos de los specs E2E REALES del área «publicación y recuperación» (navegador real + backend real, sin simulaciones).
// Los datos previos se siembran en disco o por la API real; la ACCIÓN de cada etapa certificada la hace siempre el usuario en la interfaz.
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import type { APIRequestContext, Page } from '@playwright/test';
import { listFiles, openApp, workDir } from '../support';

/** Abre un módulo por su ruta de hash con la interfaz en inglés (idioma guardado; no simula ninguna llamada a la API). */
export async function openModule(page: Page, hash: string): Promise<void> {
    await page.goto('/');
    await page.evaluate(() => { try { window.localStorage.setItem('hydra.language.v1', 'en'); } catch { /* sin almacenamiento: queda el inglés por defecto */ } });
    await openApp(page, hash);
    await page.reload();
}

export const sha256 = (data: Buffer): string => crypto.createHash('sha256').update(data).digest('hex');

/** Carpeta del fixture Sirius completo del repositorio y sus subproyectos. */
export const fixtureRoot = path.resolve(process.cwd(), '..', 'src', 'test', 'resources', 'fixtures', 'sirius-complete');
export const metamodelProject = path.join(fixtureRoot, 'com.hydra.sirius.fixture.metamodel');

export interface FixtureFile { rel: string; bytes: Buffer }

/** Ficheros regulares de una carpeta con la ruta relativa que el navegador envía al subir esa carpeta (incluye el nombre de la carpeta). */
export function folderFiles(dir: string): FixtureFile[] {
    const base = path.basename(dir);
    const out: FixtureFile[] = [];
    const walk = (current: string): void => {
        for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
            const full = path.join(current, entry.name);
            if (entry.isDirectory()) walk(full);
            else out.push({ rel: `${base}/${path.relative(dir, full).replace(/\\/g, '/')}`, bytes: fs.readFileSync(full) });
        }
    };
    walk(dir);
    return out.sort((a, b) => a.rel.localeCompare(b.rel));
}

/** Formulario multipart idéntico al que construye la interfaz (ficheros con su ruta relativa + campos de texto). */
export function multipartForm(files: FixtureFile[], fields: Record<string, string>): FormData {
    const form = new FormData();
    for (const file of files) form.append('files', new Blob([new Uint8Array(file.bytes)]), file.rel);
    for (const [name, value] of Object.entries(fields)) form.append(name, value);
    return form;
}

export interface Candidate { id: string; level: number; method: string; confidence: string; sourcePath: string; rationale: string; warnings: string[]; importable: boolean }
export interface Provenance { elementId: string; elementPath: string; origin: string; sourcePath: string; originalElement: string; method: string; confidence: string }
export interface Analysis {
    analysisId: string; selectedCandidateId: string; importable: boolean; candidates: Candidate[]; provenance: Provenance[];
    diagnostics: string[];
    repository: { repositoryFileCount: number };
    metamodelPreview: null | { metamodel: { id: string; classes: Array<{ name: string }> }; statistics: { classes: number; relations: number } };
    recoveredLayers: { palettes: Array<{ id: string }>; representations: Array<{ id: string; name: string }>; mappings: Array<{ id: string }> };
    reconciliation: { recommendedCandidateId: string; conflicts: Array<{ elementKey: string; recommendedCandidateId: string; blocking: boolean; candidateSignatures: Record<string, string> }> };
}

export interface AnalyzeFields { targetId?: string; strict?: boolean; candidateId?: string; palettes?: boolean; representations?: boolean; mappings?: boolean }

/** Llamada directa (sin navegador) al análisis real con los mismos campos que envía la interfaz. */
export async function analyzeDirect(request: APIRequestContext, files: FixtureFile[], fields: AnalyzeFields = {}): Promise<Analysis> {
    const response = await request.post('/api/update-site-imports/analyze', {
        multipart: multipartForm(files, {
            targetId: fields.targetId ?? '', strict: String(fields.strict ?? false), candidateId: fields.candidateId ?? '',
            recoverPalettes: String(fields.palettes ?? true), recoverRepresentations: String(fields.representations ?? true), recoverMappings: String(fields.mappings ?? true)
        })
    });
    if (!response.ok()) throw new Error(`analyze directo -> ${response.status()} ${await response.text()}`);
    return (await response.json()) as Analysis;
}

/** Llamada directa a una operación que recibe los ficheros y un campo `request` JSON (reconcile, materialize). */
export async function requestDirect<T>(request: APIRequestContext, url: string, files: FixtureFile[], body: unknown): Promise<T> {
    const response = await request.post(url, { multipart: multipartForm(files, { request: JSON.stringify(body) }) });
    if (!response.ok()) throw new Error(`${url} directo -> ${response.status()} ${await response.text()}`);
    return (await response.json()) as T;
}

/** Almacenes que una recuperación de update-site podría escribir (lectura recursiva del disco real del backend). */
export const RECOVERY_STORES = ['data/metamodels', 'data/editors', 'data/validations', 'data/quickfixes', 'data/assets', 'data/update-site-imports'] as const;

export function storeSnapshot(): Record<string, string[]> {
    // El análisis sí escribe una caché de artefactos direccionada por contenido (artifact-cache/, remote-cache/): no es un documento guardado.
    const isCache = (file: string): boolean => file.startsWith('artifact-cache/') || file.startsWith('remote-cache/');
    return Object.fromEntries(RECOVERY_STORES.map(store => [store, listFiles(store).filter(file => !isCache(file))]));
}

/** Texto de la etiqueta de un candidato tal y como la pinta la interfaz («Level n · método»). */
export function candidateLabel(candidate: Candidate): string {
    const human = candidate.method.replaceAll('_', ' ').toLowerCase().replace(/^./, letter => letter.toUpperCase());
    return `Level ${candidate.level} · ${human}`;
}

export { workDir };
