'use server';

import type {GuessResponse} from "@/types/review-game";
import {revalidateTag} from 'next/cache';
import {cookies} from 'next/headers';

export type GuessActionState = {
    ok: boolean;
    error?: string;
    response?: GuessResponse;
};

export async function submitGuessAction(_prev: GuessActionState | undefined, formData: FormData): Promise<GuessActionState> {
    const appIdRaw = formData.get('appId');
    const bucketGuess = formData.get('bucketGuess');

    const appId = typeof appIdRaw === 'string' ? Number.parseInt(appIdRaw, 10) : NaN;
    if (!Number.isFinite(appId) || typeof bucketGuess !== 'string' || bucketGuess.length === 0) {
        return {ok: false, error: 'Invalid input'};
    }

    try {
        const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
        const token = (await cookies()).get('s5_token')?.value;
        const url = token ? `${backend}/api/review-game/guess-auth` : `${backend}/api/review-game/guess`;
        const headers: Record<string, string> = {'content-type': 'application/json', 'accept': 'application/json'};
        if (token) headers['authorization'] = `Bearer ${token}`;
        const res = await fetch(url, {
            method: 'POST',
            headers,
            body: JSON.stringify({appId, bucketGuess}),
            // Avoid caching mutations
            cache: 'no-store',
        });
        if (!res.ok) {
            return {ok: false, error: `Upstream error ${res.status}`};
        }
        const json: GuessResponse & { steamId?: string } = await res.json();
        // Revalidate cached data tied to leaderboards and this user's profile
        revalidateTag('leaderboard');
        // Also invalidate today's leaderboard variants
        revalidateTag('leaderboard:today');
        revalidateTag('leaderboard:weekly');
        revalidateTag('leaderboard:weekly:floating');
        revalidateTag('leaderboard:all');
        // Attempt to get steamId from upstream response if present
        const steamId = json.steamId;
        if (steamId) {
            revalidateTag(`profile:${steamId}`);
        }
        return {ok: true, response: json};
    } catch (e) {
        return {ok: false, error: e instanceof Error ? e.message : 'Unknown error'};
    }
}


