"use client";

import {useRouter} from "next/navigation";
import {Routes} from "../../app/routes";
import {useAuthSignedIn} from "@/contexts/AuthContext";
import {clearDay} from "@/lib/storage";
import {notifyStoredDayChanged} from "@/components/round/useStoredDay";

export default function ResetTodayButton() {
    const signedIn = useAuthSignedIn();
    const router = useRouter()

    const onReset = () => {
        // Stored days are keyed by the backend's UTC game date. The browser's local date is a
        // *different* archive day in UTC-negative zones, so never clear it here.
        clearDay(new Date().toISOString().slice(0, 10));
        notifyStoredDayChanged();
        try {
            router.push(Routes.reviewGuesser + '/1')
        } catch { /* noop */
        }
    };

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


