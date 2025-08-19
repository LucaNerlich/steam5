"use client";

import {useEffect, useState} from "react";

type FontChoice = "krypton" | "neon" | 'argon' | 'radon' | 'xenon';

export default function FontToggle() {
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

    useEffect(() => {
        const root = document.documentElement;
        if (font === 'krypton') root.removeAttribute('data-font');
        else root.setAttribute('data-font', font);
        window.localStorage.setItem('font-family-choice', font);
    }, [font]);

    return (
        <select
            aria-label="Font family"
            className="font-select"
            value={font}
            onChange={(e) => setFont(e.target.value as FontChoice)}
        >
            <option value="krypton">Krypton</option>
            <option value="neon">Neon</option>
            <option value="argon">Argon</option>
            <option value="radon">Radon</option>
            <option value="xenon">Xenon</option>
        </select>
    );
}


