/** Indica si una ruta del proyecto corresponde a una imagen admitida por el selector de assets. */
export function isImageAsset(path: string): boolean { return /\.(svg|png|jpe?g|gif|webp)$/i.test(path.trim()); }
