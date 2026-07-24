import {BACKEND_ORIGIN} from "@/lib/backend";

export type PerfectDay = {
    steamId: string;
    personaName: string;
    gameDate: string;
    appNames: string[];
};

export async function fetchPerfectDays(): Promise<PerfectDay[] | null> {
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/stats/game/perfect-days`, {
            headers: {"accept": "application/json"},
            next: {revalidate: 3600, tags: ["stats-perfect-days"]},
        });
        if (!res.ok) return null;
        return await res.json() as PerfectDay[];
    } catch {
        return null;
    }
}
