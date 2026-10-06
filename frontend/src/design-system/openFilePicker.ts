/**
 * Opens a browser file/directory input from the current user gesture.
 * `showPicker()` is preferred because Chromium can ignore synthetic `.click()`
 * calls in some embedded/desktop configurations; `.click()` remains the
 * standards-compatible fallback for browsers that do not expose showPicker.
 */
export function openFileInputPicker(input: HTMLInputElement | null): boolean {
    if (input === null || input.disabled) return false;

    const picker = (input as HTMLInputElement & { showPicker?: () => void }).showPicker;
    if (typeof picker === 'function') {
        try {
            picker.call(input);
            return true;
        } catch {
            // Security/embedding policies may reject showPicker even during a
            // direct user gesture. The native click path is the safe fallback.
        }
    }
    input.click();
    return true;
}
