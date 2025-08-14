"use client";

import {useEffect, useState} from "react";

type FontChoice = "krypton" | "neon";

export default function FontToggle() {
    const [font, setFont] = useState<FontChoice>(() => {
        if (typeof window === 'undefined') return 'krypton';
        const stored = window.localStorage.getItem('font-family-choice');
        return stored === 'neon' || stored === 'krypton' ? stored : 'krypton';
    });

    useEffect(() => {
        const root = document.documentElement;
        if (font === 'neon') {
            root.setAttribute('data-font', 'neon');
        } else {
            root.removeAttribute('data-font');
        }
        window.localStorage.setItem('font-family-choice', font);
    }, [font]);

    return (
        <button
            className="theme-toggle"
            aria-label="Toggle font family"
            onClick={() => setFont(prev => prev === 'krypton' ? 'neon' : 'krypton')}
        >
            {font === 'neon' ? 'Font: Neon' : 'Font: Krypton'}
        </button>
    );
}


