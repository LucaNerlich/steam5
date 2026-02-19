"use client";

import {useCallback} from "react";
import {useRouter} from "next/navigation";
import {Routes} from "../../app/routes";
import {useAuthSignedIn} from "@/contexts/AuthContext";

function getLocalDateYYYYMMDD(): string {
    const now = new Date();
    const y = String(now.getFullYear());
    const m = String(now.getMonth() + 1).padStart(2, '0');
    const d = String(now.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
}

export default function ResetTodayButton() {
    const signedIn = useAuthSignedIn();
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
            for (let i = 0; i < localStorage.length; i++) {
                const key = localStorage.key(i);
                if (key && key.startsWith(prefix)) {
                    candidates.add(key);
                }
            }
            candidates.forEach((k) => localStorage.removeItem(k));
        } catch {
            // ignore
        }
        try {
            router.push(Routes.reviewGuesser + '/1')
        } catch { /* noop */
        }
    }, [router]);

    if (signedIn) return null;

    return (
        <button title='Reset Today (Cheat)'
                className="theme-toggle"
                onClick={onReset}
                aria-label="Reset today's progress">
            🤡
        </button>
    );
}


