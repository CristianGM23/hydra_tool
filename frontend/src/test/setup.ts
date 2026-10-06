import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

class TestResizeObserver implements ResizeObserver {
    observe(): void { /* Browser layout is not measured by jsdom. */ }
    unobserve(): void { /* Browser layout is not measured by jsdom. */ }
    disconnect(): void { /* Browser layout is not measured by jsdom. */ }
}

if (globalThis.ResizeObserver === undefined) globalThis.ResizeObserver = TestResizeObserver;
if (window.ResizeObserver === undefined) window.ResizeObserver = TestResizeObserver;
if (window.requestAnimationFrame === undefined) window.requestAnimationFrame = callback => window.setTimeout(() => callback(performance.now()), 0);
if (window.cancelAnimationFrame === undefined) window.cancelAnimationFrame = handle => window.clearTimeout(handle);
globalThis.requestAnimationFrame = window.requestAnimationFrame.bind(window);
globalThis.cancelAnimationFrame = window.cancelAnimationFrame.bind(window);

// Axe audits the representative document as a whole. Initialize the same minimum
// metadata expected from the real application before the first test runs.
document.documentElement.lang = 'en';
document.title = 'HYDRA test document';

class TestDOMMatrixReadOnly {
    readonly m22 = 1;
}
if (window.DOMMatrixReadOnly === undefined) window.DOMMatrixReadOnly = TestDOMMatrixReadOnly as unknown as typeof DOMMatrixReadOnly;
if (globalThis.DOMMatrixReadOnly === undefined) globalThis.DOMMatrixReadOnly = window.DOMMatrixReadOnly;


afterEach(() => {
    cleanup();
    // Keep tests independent from user preferences persisted by other suites
    // (language, creation/detail modes, panel state, etc.).
    window.localStorage.clear();
    window.sessionStorage.clear();
    document.documentElement.lang = 'en';
    document.title = 'HYDRA test document';
    delete document.documentElement.dataset.hydraLanguage;
});