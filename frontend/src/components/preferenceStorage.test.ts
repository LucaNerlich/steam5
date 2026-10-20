// @vitest-environment happy-dom
import {afterEach, describe, expect, it, vi} from 'vitest';
import {readStoredTheme, writeStoredTheme} from './ThemeToggle';
import {readStoredFont} from './FontToggle';

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('preference storage when site data is blocked', () => {
    it('reads the theme as null instead of throwing', () => {
        const getItem = vi.fn(() => { throw new Error('blocked'); });
        vi.stubGlobal('localStorage', {getItem, setItem: () => { throw new Error('blocked'); }});
        expect(readStoredTheme()).toBeNull();
        expect(getItem).toHaveBeenCalledWith('theme');
    });

    it('reads the font as null instead of throwing', () => {
        const getItem = vi.fn(() => { throw new Error('blocked'); });
        vi.stubGlobal('localStorage', {getItem, setItem: () => { throw new Error('blocked'); }});
        expect(readStoredFont()).toBeNull();
        expect(getItem).toHaveBeenCalledWith('font-family-choice');
    });

    it('writes the theme without recursing and survives a throwing write', () => {
        const setItem = vi.fn();
        vi.stubGlobal('localStorage', {getItem: () => null, setItem});
        writeStoredTheme('dark');
        expect(setItem).toHaveBeenCalledWith('theme', 'dark');

        vi.stubGlobal('localStorage', {
            getItem: () => null,
            setItem: () => { throw new Error('blocked'); },
        });
        expect(() => writeStoredTheme('light')).not.toThrow();
    });
});
