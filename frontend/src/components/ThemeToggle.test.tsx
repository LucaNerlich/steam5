// @vitest-environment happy-dom
import {act} from 'react';
import {createRoot} from 'react-dom/client';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import ThemeToggle from './ThemeToggle';

type Listener = (event: {matches: boolean}) => void;
const listeners: Listener[] = [];
let matches = false;

beforeEach(() => {
    (globalThis as {IS_REACT_ACT_ENVIRONMENT?: boolean}).IS_REACT_ACT_ENVIRONMENT = true;
    window.localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    listeners.length = 0;
    matches = false;
    vi.stubGlobal('matchMedia', () => ({
        get matches() { return matches; },
        addEventListener: (_: string, fn: Listener) => listeners.push(fn),
        removeEventListener: () => undefined,
    }));
});

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('ThemeToggle OS following', () => {
    it('keeps following the OS after the first change instead of pinning a choice', async () => {
        const container = document.createElement('div');
        document.body.appendChild(container);
        const root = createRoot(container);
        await act(async () => root.render(<ThemeToggle/>));

        matches = true;
        await act(async () => listeners.forEach((fn) => fn({matches: true})));
        expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
        expect(window.localStorage.getItem('theme')).toBeNull();

        matches = false;
        await act(async () => listeners.forEach((fn) => fn({matches: false})));
        expect(document.documentElement.getAttribute('data-theme')).toBeNull();

        act(() => root.unmount());
        container.remove();
    });
});
