"use client";

import {useEffect, useState, useSyncExternalStore} from "react";

type FontChoice = "krypton" | "neon" | 'argon' | 'radon' | 'xenon' | 'space' | 'pixel-square';

const emptySubscribe = () => () => {};

export function readStoredFont(): string | null {
    try {
        return window.localStorage.getItem('font-family-choice');
    } catch {
        // Site data blocked: fall back to the default font instead of aborting hydration.
        return null;
    }
}

const FONT_STACK = 'system-ui, -apple-system, Segoe UI, Roboto, Ubuntu, Cantarell, Noto Sans, sans-serif';

/** One font list: value drives both the CSS variable and the stored choice. */
const FONT_OPTIONS: ReadonlyArray<{ value: FontChoice; label: string }> = [
    {value: 'krypton', label: 'Krypton'},
    {value: 'neon', label: 'Neon'},
    {value: 'argon', label: 'Argon'},
    {value: 'radon', label: 'Radon'},
    {value: 'xenon', label: 'Xenon'},
    {value: 'space', label: 'Space'},
    {value: 'pixel-square', label: 'Pixel'},
];

export default function FontToggle() {
    // Gate rendering on hydration without a synchronous setState in an effect (which
    // the React Compiler can't optimize): useSyncExternalStore serves the server
    // snapshot (false) during hydration and flips to true right after.
    const mounted = useSyncExternalStore(emptySubscribe, () => true, () => false);
    const [font, setFont] = useState<FontChoice>(() => {
        if (typeof window === 'undefined') return 'krypton';
        const stored = readStoredFont();
        switch (stored) {
            case 'neon':
            case 'argon':
            case 'radon':
            case 'xenon':
            case 'space':
            case 'pixel-square':
            case 'krypton':
                return stored as FontChoice;
            default:
                return 'krypton';
        }
    });

    useEffect(() => {
        if (!font) return;
        const root = document.documentElement;
        if (font === 'krypton') root.removeAttribute('data-font');
        else root.setAttribute('data-font', font);
        // persist only when actually changed
        const current = readStoredFont();
        if (current !== font) {
            try {
                window.localStorage.setItem('font-family-choice', font);
            } catch {
                // Persistence unavailable; the selected font still applies for this visit.
            }
        }
    }, [font]);

    if (!mounted) return null;

    return (
        <select
            aria-label="Font family"
            className="font-select"
            value={font}
            onChange={(e) => setFont(e.target.value as FontChoice)}
        >
            {FONT_OPTIONS.map(({value, label}) => (
                <option
                    key={value}
                    value={value}
                    style={{fontFamily: `var(--font-${value}), ${FONT_STACK}`}}
                >
                    {label}
                </option>
            ))}
        </select>
    );
}


