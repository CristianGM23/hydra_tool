// Ayudantes compartidos de los specs E2E REALES del área «project» (navegador real + backend real, sin simulaciones).
// El backend corre en esta misma máquina: sembramos proyectos con la API real y leemos/escribimos su carpeta en disco
// (oráculo independiente del navegador). La ACCIÓN de cada etapa certificada la hace siempre el usuario en la interfaz.
import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import type { APIRequestContext } from '@playwright/test';
import { apiGet, uid, workDir } from '../support';

export interface SeededProject {
    id: string;
    name: string;
    /** Carpeta absoluta del proyecto en disco. */
    directory: string;
    /** Carpeta del proyecto relativa al directorio de trabajo del backend (para readJson/exists de support.ts). */
    rel: string;
    /** Carpeta padre absoluta en la que se creó. */
    parent: string;
}

export interface SeedOptions {
    /** Si se omite, el backend crea el proyecto «legacy» con todas las capacidades y modo libre. */
    preset?: string;
    capabilities?: string[];
    mode?: 'GUIDED' | 'FREE';
}

export interface SessionJson {
    active: boolean;
    directory: string;
    restoredTabs: string[];
    project: null | {
        id: string; name: string; enabledCapabilities: string[]; experienceMode: string;
        primaryArtifact: { kind: string; id: string; relativePath: string };
    };
}

export interface TreeNodeJson {
    id: string; name: string; relativePath: string; nodeType: string; artifactKind: string; etag: string; children: TreeNodeJson[];
}

export const toRel = (absolute: string): string => path.relative(workDir, absolute).replace(/\\/g, '/');

/** Crea un proyecto real con la API de HYDRA (queda como proyecto activo del backend y en la lista de recientes). */
export async function seedProject(request: APIRequestContext, label: string, options: SeedOptions = {}): Promise<SeededProject> {
    const name = uid(label);
    const parent = path.join(workDir, 'e2e-projects', uid('p'));
    fs.mkdirSync(parent, { recursive: true });
    const data: Record<string, unknown> = { parentDirectory: parent, projectId: name, name, description: '', initializeFromCurrentWorkspace: false };
    if (options.preset !== undefined) data.preset = options.preset;
    if (options.capabilities !== undefined) data.enabledCapabilities = options.capabilities;
    if (options.mode !== undefined) data.experienceMode = options.mode;
    const response = await request.post('/api/projects', { data });
    if (!response.ok()) throw new Error(`POST /api/projects -> ${response.status()} ${await response.text()}`);
    const directory = path.join(parent, name);
    if (!fs.existsSync(path.join(directory, 'modeling-project.json'))) throw new Error('el backend no escribió modeling-project.json en ' + directory);
    return { id: name, name, directory, rel: toRel(directory), parent };
}

/** Reabre el proyecto por la API (reinicia la línea base del detector de cambios y deja el proyecto activo). */
export async function reopenProject(request: APIRequestContext, project: SeededProject): Promise<SessionJson> {
    const response = await request.post('/api/projects/open', { data: { projectDirectory: project.directory, loadIntoCurrentWorkspace: false } });
    if (!response.ok()) throw new Error(`POST /api/projects/open -> ${response.status()} ${await response.text()}`);
    return (await response.json()) as SessionJson;
}

export function writeProjectFile(project: SeededProject, relativePath: string, content: string | Buffer): string {
    const target = path.join(project.directory, relativePath);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, content);
    return target;
}

export function readProjectFile(project: SeededProject, relativePath: string): Buffer {
    return fs.readFileSync(path.join(project.directory, relativePath));
}

export const session = (request: APIRequestContext): Promise<SessionJson> => apiGet<SessionJson>(request, '/api/projects/session');

export function flattenTree(node: TreeNodeJson): TreeNodeJson[] {
    return [node, ...node.children.flatMap(flattenTree)];
}

/** Lector ZIP independiente del backend (directorio central + inflate de Node): nombre de entrada -> bytes. */
export function readZipEntries(zip: Buffer): Map<string, Buffer> {
    let eocd = -1;
    for (let i = zip.length - 22; i >= 0; i--) { if (zip.readUInt32LE(i) === 0x06054b50) { eocd = i; break; } }
    if (eocd < 0) throw new Error('no es un ZIP válido (falta el registro final)');
    const count = zip.readUInt16LE(eocd + 10);
    let cursor = zip.readUInt32LE(eocd + 16);
    const entries = new Map<string, Buffer>();
    for (let n = 0; n < count; n++) {
        if (zip.readUInt32LE(cursor) !== 0x02014b50) throw new Error('directorio central dañado');
        const method = zip.readUInt16LE(cursor + 10);
        const compressedSize = zip.readUInt32LE(cursor + 20);
        const nameLength = zip.readUInt16LE(cursor + 28);
        const extraLength = zip.readUInt16LE(cursor + 30);
        const commentLength = zip.readUInt16LE(cursor + 32);
        const localOffset = zip.readUInt32LE(cursor + 42);
        const name = zip.subarray(cursor + 46, cursor + 46 + nameLength).toString('utf8');
        const dataStart = localOffset + 30 + zip.readUInt16LE(localOffset + 26) + zip.readUInt16LE(localOffset + 28);
        const raw = zip.subarray(dataStart, dataStart + compressedSize);
        entries.set(name, method === 0 ? Buffer.from(raw) : zlib.inflateRawSync(raw));
        cursor += 46 + nameLength + extraLength + commentLength;
    }
    return entries;
}

/** Nombres de entrada que son ficheros (no directorios). */
export const zipFileNames = (entries: Map<string, Buffer>): string[] => [...entries.keys()].filter(name => !name.endsWith('/')).sort();
