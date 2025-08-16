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
            title='Font Toggle'
            className="theme-toggle"
            aria-label="Toggle font family"
            onClick={() => setFont(prev => prev === 'krypton' ? 'neon' : 'krypton')}
        >
            {font === 'neon' ? (
                // Show next target: Krypton ⇒ display "K"
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"
                     focusable="false">
                    <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeWidth="1.5"/>
                    <text x="12" y="12" fill="currentColor" fontSize="11" textAnchor="middle"
                          dominantBaseline="central">K
                    </text>
                </svg>
            ) : (
                // Show next target: Neon ⇒ display "N"
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"
                     focusable="false">
                    <circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" strokeWidth="1.5"/>
                    <text x="12" y="12" fill="currentColor" fontSize="11" textAnchor="middle"
                          dominantBaseline="central">N
                    </text>
                </svg>
            )}
        </button>
    );
}


