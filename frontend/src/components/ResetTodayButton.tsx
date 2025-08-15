"use client";

import {useCallback, useEffect, useState} from "react";
import {useRouter} from "next/navigation";
import {Routes} from "../../app/routes";

function getLocalDateYYYYMMDD(): string {
    const now = new Date();
    const y = String(now.getFullYear());
    const m = String(now.getMonth() + 1).padStart(2, '0');
    const d = String(now.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
}

export default function ResetTodayButton() {
    const [done, setDone] = useState(false);
    const [signedIn, setSignedIn] = useState<boolean>(false);
    const router = useRouter()

    const onReset = useCallback(() => {
        try {
            const prefix = 'review-guesser:';
            const utcToday = new Date().toISOString().slice(0, 10);
            const localToday = getLocalDateYYYYMMDD();
            const candidates = new Set<string>([
                `${prefix}${utcToday}`,
                `${prefix}${localToday}`,
            ]);
            // Also try to remove the most recent matching key if present
            for (let i = 0; i < localStorage.length; i++) {
                const key = localStorage.key(i);
                if (key && key.startsWith(prefix)) {
                    candidates.add(key);
                }
            }
            candidates.forEach((k) => localStorage.removeItem(k));
            setDone(true);
            setTimeout(() => setDone(false), 1500);
        } catch {
            // ignore
        }
        // Optionally refresh the page to reflect cleared progress
        try {
            router.push(Routes.reviewGuesser + '/1')
        } catch { /* noop */
        }
    }, []);

    useEffect(() => {
        let active = true;

        async function load() {
            try {
                const res = await fetch('/api/auth/me', {cache: 'no-store'});
                if (!res.ok) return;
                const data = await res.json();
                if (active) setSignedIn(Boolean(data?.signedIn));
            } catch {
            }
        }

        load();
        return () => {
            active = false
        };
    }, []);

    if (signedIn) return null;

    return (
        <button className="theme-toggle" onClick={onReset} aria-label="Reset today's progress">
            {done ? 'Reset ✓' : 'Reset Today 🤡'}
        </button>
    );
}


