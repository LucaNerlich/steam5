import type {ReactNode} from "react";
import {RoundPresenceProvider} from "@/contexts/RoundPresenceContext";
import {BACKEND_ORIGIN as backend, BACKEND_TIMEOUT_MS } from "@/lib/backend";

export const revalidate = 60;

/**
 * Loads the current review game date.
 *
 * @returns The game date, or `null` if it cannot be loaded or is unavailable.
 */
async function loadTodayDate(): Promise<string | null> {
    try {
        const res = await fetch(`${backend}/api/review-game/today`, {
            signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS),
            headers: {"accept": "application/json"},
            next: {revalidate: 60, tags: ["round-today"]},
        });
        if (!res.ok) return null;
        const data = await res.json() as {date?: string};
        return typeof data?.date === "string" ? data.date : null;
    } catch {
        return null;
    }
}

/**
 * Provides review-guesser content with presence scoped to today's game date.
 *
 * The provider stays mounted even when the date is unavailable: its consumers require
 * the context, and an empty scope simply does not open a socket.
 *
 * @returns The review-guesser content wrapped with round presence context.
 */
export default async function ReviewGuesserRoundLayout({children}: {children: ReactNode}) {
    const gameDate = await loadTodayDate();
    return (
        <RoundPresenceProvider scopeKey={gameDate ?? ""}>
            {children}
        </RoundPresenceProvider>
    );
}
