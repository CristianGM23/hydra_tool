/** Patrones dinámicos visibles en importación/exportación y otros overlays. */
export function translateCurrentRuntimeSpanish(text: string): string | null {
    let match = text.match(/^(\d+) of (\d+) stages completed$/);
    if (match !== null) return `${match[1]} de ${match[2]} etapas completadas`;
    match = text.match(/^· current stage (.+)$/);
    if (match !== null) return `· etapa actual ${match[1]}`;
    match = text.match(/^(exact|full|partial|experimental|best effort|best-effort) support for (.+) files\.$/i);
    if (match !== null) {
        const levels: Readonly<Record<string, string>> = { exact: 'Compatibilidad exacta', full: 'Compatibilidad completa', partial: 'Compatibilidad parcial', experimental: 'Compatibilidad experimental', 'best effort': 'Compatibilidad aproximada', 'best-effort': 'Compatibilidad aproximada' };
        return `${levels[match[1].toLowerCase()] ?? match[1]} para archivos ${match[2]}.`;
    }
    match = text.match(/^Forced importer: (.+)\.$/);
    if (match !== null) return `Importador forzado: ${match[1]}.`;
    match = text.match(/^Overwrite (allowed|blocked) · strict semantic preservation (enabled|disabled)\.$/);
    if (match !== null) return `Sobrescritura ${match[1] === 'allowed' ? 'permitida' : 'bloqueada'} · conservación semántica estricta ${match[2] === 'enabled' ? 'activada' : 'desactivada'}.`;
    match = text.match(/^Analysis status: (.+)\.$/);
    if (match !== null) return `Estado del análisis: ${match[1]}.`;
    match = text.match(/^(\d+) format categor(y|ies)$/);
    if (match !== null) return `${match[1]} ${match[1] === '1' ? 'categoría de formato' : 'categorías de formato'}`;
    match = text.match(/^(\d+) format\(s\) ready$/);
    if (match !== null) return `${match[1]} formato(s) listo(s)`;
    match = text.match(/^(Download selected|Save selected remotely) \((\d+)\)$/);
    if (match !== null) return `${match[1] === 'Download selected' ? 'Descargar seleccionados' : 'Guardar seleccionados de forma remota'} (${match[2]})`;
    match = text.match(/^Download (.+) log$/);
    if (match !== null) return `Descargar registro de ${match[1]}`;
    match = text.match(/^Download log for (.+)$/);
    if (match !== null) return `Descargar registro de ${match[1]}`;
    match = text.match(/^(\d+) class\(es\), (\d+) enum\(s\), (\d+) datatype\(s\)\.$/);
    if (match !== null) return `${match[1]} clase(s), ${match[2]} enumeración(es), ${match[3]} tipo(s) de datos.`;
    match = text.match(/^(\d+) relation\(s\) currently defined\. Relations are optional\.$/);
    if (match !== null) return `${match[1]} relación(es) definida(s) actualmente. Las relaciones son opcionales.`;
    match = text.match(/^(\d+) (classes|enums|data types|attributes|relations)$/);
    if (match !== null) {
        const nouns: Readonly<Record<string, string>> = { classes: 'clases', enums: 'enumeraciones', 'data types': 'tipos de datos', attributes: 'atributos', relations: 'relaciones' };
        return `${match[1]} ${nouns[match[2]]}`;
    }
    match = text.match(/^(\d+) information-loss message\(s\)$/);
    if (match !== null) return `${match[1]} mensaje(s) de pérdida de información`;
    match = text.match(/^(\d+) block type\(s\) recovered$/);
    if (match !== null) return `${match[1]} tipo(s) de bloque recuperado(s)`;
    match = text.match(/^(\d+) field\(s\) mapped to attributes$/);
    if (match !== null) return `${match[1]} campo(s) mapeado(s) a atributos`;
    match = text.match(/^(\d+) input\(s\) mapped to relations$/);
    if (match !== null) return `${match[1]} entrada(s) mapeada(s) a relaciones`;
    match = text.match(/^(\d+) connection check\(s\) preserved$/);
    if (match !== null) return `${match[1]} restricción(es) de conexión conservada(s)`;
    match = text.match(/^JavaScript executed: (.+)\. Open the preview to edit the neutral classes, attributes and relations before saving\.$/);
    if (match !== null) return `JavaScript ejecutado: ${match[1]}. Abre la vista previa para editar las clases, atributos y relaciones neutrales antes de guardar.`;
    match = text.match(/^(.+) This dependency is required by the source bundle\.$/);
    if (match !== null) return `${translateCurrentRuntimeSpanish(match[1]) ?? match[1]} Esta dependencia es obligatoria para el bundle de origen.`;
    match = text.match(/^(.+) This dependency is declared optional, so the main bundle may still work with reduced capability\.$/);
    if (match !== null) return `${translateCurrentRuntimeSpanish(match[1]) ?? match[1]} Esta dependencia está declarada como opcional, por lo que el bundle principal puede seguir funcionando con capacidad reducida.`;
    match = text.match(/^Level (\d+) · (.+)$/);
    if (match !== null) return `Nivel ${match[1]} · ${match[2]}`;
    match = text.match(/^(\d+) parser rules$/);
    if (match !== null) return `${match[1]} reglas de parser`;
    match = text.match(/^(\d+) terminals$/);
    if (match !== null) return `${match[1]} terminales`;
    match = text.match(/^(\d+) assignments$/);
    if (match !== null) return `${match[1]} asignaciones`;
    match = text.match(/^(\d+) cross refs$/);
    if (match !== null) return `${match[1]} referencias cruzadas`;
    match = text.match(/^Possible direct left recursion: (.+)$/);
    if (match !== null) return `Posible recursión directa por la izquierda: ${match[1]}`;
    return null;
}
