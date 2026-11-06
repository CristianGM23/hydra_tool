import fs from 'node:fs';
import { expect, test } from '@playwright/test';
import { apiGet, uid } from '../support';
import { openMetamodelDesigner, openToolbarMenu, seedMetamodel } from './helpers';

// Jornada: diálogo «Export metamodel». Acción: elegir metamodelo guardado, formato y destino local, y descargar con el navegador.
// Oráculo: los ficheros descargados se abren desde Node (JSON y ZIP) y se contrastan con el catálogo de formatos y el metamodelo de la API real.

interface ExportFormat { id: string; displayName: string; extension: string; category: string }

/** Nombres de las entradas de un ZIP leyendo su directorio central (sin depender del navegador ni de librerías). */
function zipEntryNames(buffer: Buffer): string[] {
    let end = -1;
    for (let offset = buffer.length - 22; offset >= 0; offset -= 1) {
        if (buffer.readUInt32LE(offset) === 0x06054b50) { end = offset; break; }
    }
    if (end < 0) throw new Error('el fichero descargado no es un ZIP válido');
    const count = buffer.readUInt16LE(end + 10);
    let cursor = buffer.readUInt32LE(end + 16);
    const names: string[] = [];
    for (let index = 0; index < count; index += 1) {
        if (buffer.readUInt32LE(cursor) !== 0x02014b50) throw new Error('directorio central del ZIP dañado');
        const nameLength = buffer.readUInt16LE(cursor + 28);
        const extraLength = buffer.readUInt16LE(cursor + 30);
        const commentLength = buffer.readUInt16LE(cursor + 32);
        names.push(buffer.toString('utf8', cursor + 46, cursor + 46 + nameLength));
        cursor += 46 + nameLength + extraLength + commentLength;
    }
    return names;
}

test('el diálogo de exportación exige un formato y descarga el metamodelo elegido en JSON y en un ZIP con los formatos marcados', async ({ page, request }) => {
    const id = uid('exp');
    const name = uid('Export metamodel');
    await seedMetamodel(request, id, name);

    await openMetamodelDesigner(page);
    // Rechazo previo: un borrador sin guardar no se puede exportar (el asistente guiado bloquea el botón).
    await openToolbarMenu(page, 'Interchange');
    await expect(page.locator('.metamodel-export-button')).toBeDisabled();
    await page.keyboard.press('Escape');

    // El usuario elige el metamodelo guardado y lo abre (las órdenes están en desplegables de la barra).
    await page.getByLabel('Existing metamodel', { exact: true }).selectOption(id);
    await openToolbarMenu(page, 'Metamodel');
    await page.getByRole('group', { name: 'Metamodel' }).getByRole('button', { name: 'Open', exact: true }).click();
    await expect(page.locator('.metamodel-success-message')).toContainText(`Opened ${name}`);
    await openToolbarMenu(page, 'Interchange');
    await expect(page.locator('.metamodel-export-button')).toBeEnabled();
    await page.locator('.metamodel-export-button').click();

    const dialog = page.getByRole('dialog', { name: /Export metamodel|Exportar metamodelo/i });
    await expect(dialog).toBeVisible();
    await expect(dialog).toContainText(id); // el diálogo declara para qué metamodelo exporta
    await expect(dialog.getByRole('button', { name: /Local file|Archivo local/i }).first()).toHaveClass(/active/); // destino local

    // Catálogo de formatos según el backend (oráculo independiente del navegador).
    const formats = await apiGet<ExportFormat[]>(request, `/api/metamodels/${id}/export-formats`);
    const json = formats.find(item => item.id === 'json') ?? formats.find(item => item.category === 'SEMANTIC' && item.extension.toLowerCase().endsWith('json'));
    expect(json, 'el backend debe ofrecer el formato JSON neutro').toBeDefined();
    const second = formats.find(item => item.category !== 'BUNDLE' && item.id !== json?.id && item.category === 'SEMANTIC');
    expect(second, 'se necesita un segundo formato semántico para el ZIP').toBeDefined();
    const article = (formatId: string) => dialog.locator(`article.metamodel-export-option[data-export-url$="/exports/${formatId}"]`);

    // Rechazo: sin ningún formato marcado no se puede generar el paquete.
    await dialog.getByRole('button', { name: /^Clear$|^Limpiar$/i }).click();
    await expect(dialog.locator('.metamodel-export-bundle-button')).toBeDisabled();
    await expect(dialog).toContainText(/0 of \d+ formats selected/);

    // Descarga individual del JSON neutro.
    const [single] = await Promise.all([page.waitForEvent('download'), article(json!.id).locator('button').click()]);
    expect(single.suggestedFilename().toLowerCase()).toContain(json!.extension.replace(/^\./, '').toLowerCase());
    const singlePath = await single.path();
    const singleText = fs.readFileSync(singlePath, 'utf8');
    expect(() => JSON.parse(singleText)).not.toThrow();
    expect(singleText).toContain(id);
    expect(singleText).toContain('Person');
    expect(singleText).toContain('fullName');

    // Descarga del ZIP con los dos formatos marcados.
    await article(json!.id).getByRole('checkbox').check();
    await article(second!.id).getByRole('checkbox').check();
    await expect(dialog.locator('.metamodel-export-bundle-button')).toBeEnabled();
    await expect(dialog.locator('.metamodel-export-bundle-button')).toContainText('(2)');
    const [bundle] = await Promise.all([page.waitForEvent('download'), dialog.locator('.metamodel-export-bundle-button').click()]);
    const bundlePath = await bundle.path();
    const bytes = fs.readFileSync(bundlePath);
    expect(bytes.subarray(0, 2).toString('latin1')).toBe('PK');
    const entries = zipEntryNames(bytes);
    expect(entries.length).toBeGreaterThanOrEqual(2);
    expect(entries.some(entry => entry.toLowerCase().endsWith(json!.extension.replace(/^\./, '').toLowerCase()))).toBe(true);
    expect(entries.some(entry => entry.toLowerCase().endsWith(second!.extension.replace(/^\./, '').toLowerCase()))).toBe(true);

    // Exportar no modifica lo guardado.
    const stored = await apiGet<{ id: string; name: string }>(request, `/api/canonical-metamodels/${id}`);
    expect(stored).toMatchObject({ id, name });
});
