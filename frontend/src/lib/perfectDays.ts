import {BACKEND_ORIGIN} from "@/lib/backend";

export type PerfectDay = {
    steamId: string;
    personaName: string;
    avatar?: string | null;
    profileUrl?: string | null;
    gameDate: string;
    appNames: string[];
};

export type PerfectDaysFetchResult = { data: PerfectDay[]; refreshedAt: string | null };

export async function fetchPerfectDays(): Promise<PerfectDaysFetchResult | null> {
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/stats/game/perfect-days`, {
            headers: {"accept": "application/json"},
            next: {revalidate: 3600, tags: ["stats-perfect-days"]},
        });
        if (!res.ok) return null;
        const data = await res.json() as PerfectDay[];
        return {data, refreshedAt: res.headers.get('X-Leaderboard-Refreshed-At')};
    } catch {
        return null;
    }
}
