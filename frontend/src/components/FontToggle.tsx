"use client";

import {useEffect, useState, useSyncExternalStore} from "react";

type FontChoice = "krypton" | "neon" | 'argon' | 'radon' | 'xenon' | 'space' | 'pixel-square';

const emptySubscribe = () => () => {};

export default function FontToggle() {
    // Gate rendering on hydration without a synchronous setState in an effect (which
    // the React Compiler can't optimize): useSyncExternalStore serves the server
    // snapshot (false) during hydration and flips to true right after.
    const mounted = useSyncExternalStore(emptySubscribe, () => true, () => false);
    const [font, setFont] = useState<FontChoice>(() => {
        if (typeof window === 'undefined') return 'krypton';
        const stored = window.localStorage.getItem('font-family-choice');
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
        const current = window.localStorage.getItem('font-family-choice');
        if (current !== font) {
            window.localStorage.setItem('font-family-choice', font);
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
            <option
                value="krypton"
                style={{fontFamily: 'var(--font-krypton), system-ui, -apple-system, Segoe UI, Roboto, Ubuntu, Cantarell, Noto Sans, sans-serif'}}
            >
                Krypton
            </option>
            <option
                value="neon"
                style={{fontFamily: 'var(--font-neon), system-ui, -apple-system, Segoe UI, Roboto, Ubuntu, Cantarell, Noto Sans, sans-serif'}}
            >
                Neon
            </option>
            <option
                value="argon"
                style={{fontFamily: 'var(--font-argon), system-ui, -apple-system, Segoe UI, Roboto, Ubuntu, Cantarell, Noto Sans, sans-serif'}}
            >
                Argon
            </option>
            <option
                value="radon"
                style={{fontFamily: 'var(--font-radon), system-ui, -apple-system, Segoe UI, Roboto, Ubuntu, Cantarell, Noto Sans, sans-serif'}}
            >
                Radon
            </option>
            <option
                value="xenon"
                style={{fontFamily: 'var(--font-xenon), system-ui, -apple-system, Segoe UI, Roboto, Ubuntu, Cantarell, Noto Sans, sans-serif'}}
            >
                Xenon
            </option>
            <option
                value="space"
                style={{fontFamily: 'var(--font-space), system-ui, -apple-system, Segoe UI, Roboto, Ubuntu, Cantarell, Noto Sans, sans-serif'}}
            >
                Space
            </option>
            <option
                value="pixel-square"
                style={{fontFamily: 'var(--font-pixel-square), system-ui, -apple-system, Segoe UI, Roboto, Ubuntu, Cantarell, Noto Sans, sans-serif'}}
            >
                Pixel
            </option>
        </select>
    );
}


