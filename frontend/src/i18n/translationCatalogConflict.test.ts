import { describe, expect, it } from 'vitest';
import { SPANISH_CONFLICT_RESOLUTIONS, translateUiText } from './translationCatalog';

describe('HYDRA V4.15 Spanish catalogue consolidation', () => {
    it('applies explicit conflict resolutions instead of relying on catalogue spread order', () => {
        for (const [english, spanish] of Object.entries(SPANISH_CONFLICT_RESOLUTIONS)) {
            expect(translateUiText(english, 'es')).toBe(spanish);
        }
    });

    it('keeps product and technology names intact when they are standalone labels', () => {
        for (const name of ['Windows', 'Linux', 'macOS', 'Eclipse', 'Ecore', 'EMF', 'Sirius', 'Xtext', 'Langium', 'Node-RED', 'Siddhi']) {
            expect(translateUiText(name, 'es')).toBe(name);
        }
    });
});
