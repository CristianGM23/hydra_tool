/** Comprueba si un archivo seleccionado puede usarse como imagen portable del proyecto. */
export function isSupportedImage(file: Pick<File, 'name' | 'type'>): boolean {
    return /\.(svg|png|jpe?g|gif|webp)$/i.test(file.name) && (file.type === '' || ['image/svg+xml','image/png','image/jpeg','image/gif','image/webp'].includes(file.type));
}
