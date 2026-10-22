// Utilidades compartidas de los specs E2E REALES (navegador real + backend real). Sin simulaciones de la API.
import fs from 'node:fs';
import path from 'node:path';
import type { APIRequestContext, Page } from '@playwright/test';

/** Directorio de trabajo del backend real (todas las rutas relativas ./data/... caen aquí). Oráculo independiente: se lee desde Node. */
export const workDir = path.resolve(process.cwd(), '..', 'target', 'archify-v3', 'e2e', 'work');

/** Sufijo único por ejecución y spec: los specs comparten un solo backend y no pueden depender del orden. */
export function uid(prefix: string): string {
    return `${prefix}-${Date.now().toString(36)}${Math.floor(Math.random() * 1296).toString(36)}`;
}

/** Ficheros regulares bajo un directorio del backend (rutas relativas con '/'), ordenados. */
export function listFiles(relDir: string): string[] {
    const root = path.join(workDir, relDir);
    if (!fs.existsSync(root)) return [];
    const out: string[] = [];
    const walk = (dir: string): void => {
        for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
            const full = path.join(dir, entry.name);
            if (entry.isDirectory()) walk(full); else out.push(path.relative(root, full).replace(/\\/g, '/'));
        }
    };
    walk(root);
    return out.sort();
}

/** Lee y parsea un JSON del almacenamiento del backend (oráculo independiente de la API). */
export function readJson<T = unknown>(relPath: string): T {
    return JSON.parse(fs.readFileSync(path.join(workDir, relPath), 'utf8').replace(/^﻿/, '')) as T;
}

export function exists(relPath: string): boolean {
    return fs.existsSync(path.join(workDir, relPath));
}

/** Espera a que una condición sobre el disco/API se cumpla (sondeo acotado; nada de esperas fijas). */
export async function eventually<T>(probe: () => Promise<T> | T, accept: (value: T) => boolean, timeoutMs = 15_000): Promise<T> {
    const started = Date.now();
    for (;;) {
        const value = await probe();
        if (accept(value)) return value;
        if (Date.now() - started > timeoutMs) throw new Error('condición no alcanzada a tiempo; último valor: ' + JSON.stringify(value));
        await new Promise((resolve) => setTimeout(resolve, 150));
    }
}

/** GET JSON contra el backend real a través del proxy (oráculo secundario). */
export async function apiGet<T = unknown>(request: APIRequestContext, url: string): Promise<T> {
    const response = await request.get(url);
    if (!response.ok()) throw new Error(`GET ${url} -> ${response.status()}`);
    return (await response.json()) as T;
}

/** Abre la aplicación en una ruta de hash (p. ej. '/#/events') y comprueba que no hay errores crudos del servidor. */
export async function openApp(page: Page, hash = '/'): Promise<void> {
    await page.goto(hash);
    const body = page.locator('body');
    await body.waitFor({ state: 'visible' });
    for (const raw of ['BeanCreationException', 'NullPointerException', 'Whitelabel Error Page']) {
        if ((await body.innerText()).includes(raw)) throw new Error('error crudo del servidor visible: ' + raw);
    }
}
