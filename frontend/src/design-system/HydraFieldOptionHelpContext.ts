import { createContext } from 'react';

/**
 * Indica que el contenedor de campo ya presenta la ayuda completa de las opciones.
 * Evita duplicar el botón de información del selector cuando este está integrado en
 * un campo HYDRA que ya explica los valores disponibles.
 */
export const HydraFieldOptionHelpContext = createContext(false);
