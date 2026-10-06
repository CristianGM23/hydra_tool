import { describe, expect, it, vi } from 'vitest';
import { openFileInputPicker } from './openFilePicker';

describe('openFileInputPicker', () => {
    it('prefers the native showPicker API during the user gesture', () => {
        const input = document.createElement('input');
        input.type = 'file';
        input.value = '';
        const showPicker = vi.fn();
        Object.defineProperty(input, 'showPicker', { configurable: true, value: showPicker });
        const click = vi.spyOn(input, 'click');

        expect(openFileInputPicker(input)).toBe(true);
        expect(showPicker).toHaveBeenCalledOnce();
        expect(click).not.toHaveBeenCalled();
    });

    it('falls back to click when showPicker is unavailable or rejected', () => {
        const input = document.createElement('input');
        input.type = 'file';
        Object.defineProperty(input, 'showPicker', { configurable: true, value: vi.fn(() => { throw new DOMException('blocked'); }) });
        const click = vi.spyOn(input, 'click').mockImplementation(() => undefined);

        expect(openFileInputPicker(input)).toBe(true);
        expect(click).toHaveBeenCalledOnce();
    });

    it('does not try to open disabled or missing inputs', () => {
        const input = document.createElement('input');
        input.type = 'file';
        input.disabled = true;
        const click = vi.spyOn(input, 'click');

        expect(openFileInputPicker(null)).toBe(false);
        expect(openFileInputPicker(input)).toBe(false);
        expect(click).not.toHaveBeenCalled();
    });
});
