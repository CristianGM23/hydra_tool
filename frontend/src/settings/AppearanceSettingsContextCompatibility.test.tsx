import { describe, expect, it } from 'vitest';
import { AppearanceSettingsProvider, useAppearanceSettings } from './AppearanceSettingsContext';

describe('AppearanceSettingsContext compatibility', () => {
    it('keeps the provider and historical hook export on the original module path', () => {
        expect(AppearanceSettingsProvider).toBeTypeOf('function');
        expect(useAppearanceSettings).toBeTypeOf('function');
    });
});
