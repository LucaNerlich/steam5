import type {ReactNode} from "react";
import {RoundPresenceProvider} from "@/contexts/RoundPresenceContext";
import {BACKEND_ORIGIN as backend} from "@/lib/backend";

export const revalidate = 60;

async function loadTodayDate(): Promise<string | null> {
    try {
        const res = await fetch(`${backend}/api/year-game/today`, {
            headers: {"accept": "application/json"},
            next: {revalidate: 60, tags: ["year-round-today"]},
        });
        if (!res.ok) return null;
        const data = await res.json() as {date?: string};
        return typeof data?.date === "string" ? data.date : null;
    } catch {
        return null;
    }
}

export default async function YearGuesserRoundLayout({children}: {children: ReactNode}) {
    const gameDate = await loadTodayDate();
    if (!gameDate) {
        return <>{children}</>;
    }
    return (
        <RoundPresenceProvider scopeKey={`year:${gameDate}`}>
            {children}
        </RoundPresenceProvider>
    );
}
