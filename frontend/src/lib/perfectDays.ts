import {fetchBackendJson} from "@/lib/backend";

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
    const {data, headers} = await fetchBackendJson<PerfectDay[]>(
        '/api/stats/game/perfect-days',
        {revalidate: 3600, tags: ["stats-perfect-days"]},
    );
    if (!data) return null;
    return {data, refreshedAt: headers.get('X-Leaderboard-Refreshed-At')};
}
