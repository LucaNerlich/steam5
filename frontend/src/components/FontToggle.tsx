"use client";

import {useEffect, useState} from "react";

type FontChoice = "krypton" | "neon" | 'argon' | 'radon' | 'xenon';

export default function FontToggle() {
    const [mounted, setMounted] = useState(false);
    const [font, setFont] = useState<FontChoice>(() => {
        if (typeof window === 'undefined') return 'krypton';
        const stored = window.localStorage.getItem('font-family-choice');
        switch (stored) {
            case 'neon':
            case 'argon':
            case 'radon':
            case 'xenon':
            case 'krypton':
                return stored as FontChoice;
            default:
                return 'krypton';
        }
    });

    // After hydration, ensure we sync with persisted choice from localStorage
    useEffect(() => {
        try {
            const stored = window.localStorage.getItem('font-family-choice');
            if (stored === 'neon' || stored === 'argon' || stored === 'radon' || stored === 'xenon' || stored === 'krypton') {
                if (stored !== font) setFont(stored as FontChoice);
            }
        } catch {
            // ignore
        } finally {
            setMounted(true);
        }
    }, []);

    useEffect(() => {
        const root = document.documentElement;
        if (font === 'krypton') root.removeAttribute('data-font');
        else root.setAttribute('data-font', font);
        window.localStorage.setItem('font-family-choice', font);
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
        </select>
    );
}


