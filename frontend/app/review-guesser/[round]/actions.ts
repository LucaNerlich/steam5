'use server';

import type {GuessResponse} from "@/types/review-game";
import {cookies} from 'next/headers';
import {revalidatePath, revalidateTag} from 'next/cache';

// Bust the cached daily picks after the nightly rollover. The round page is
// force-static with `revalidate = 600` and the `/today` fetch is tagged
// 'round-today', so without an explicit invalidation Next keeps serving the
// previous day's statically-cached round until the TTL lapses (or an auth event
// fires). The client calls this when it detects a stale gameDate so the next
// render resolves today's picks. Mirrors the auth callback's invalidation.
export async function revalidateTodayAction(): Promise<void> {
    revalidateTag('round-today', 'max');
    revalidatePath('/review-guesser', 'layout');
}

export type GuessActionState = {
    ok: boolean;
    error?: string;
    response?: GuessResponse;
    // The session cookie was present but the backend rejected it (401). The user
    // believes they are signed in, but they are not.
    unauthorized?: boolean;
    // Whether the guess was saved under an authenticated identity. False means it
    // was submitted anonymously (no cookie) and does not count toward the leaderboard.
    persisted?: boolean;
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
            // A 401 on the authenticated endpoint means the cookie is present but
            // invalid/expired — the user thinks they are signed in but is not.
            if (token && res.status === 401) {
                return {ok: false, unauthorized: true, error: 'unauthorized'};
            }
            return {ok: false, error: `Upstream error ${res.status}`};
        }
        const json: GuessResponse = await res.json();
        // persisted is true only when an authenticated request succeeded; a guess
        // sent to the anonymous endpoint (no cookie) is not saved to the leaderboard.
        return {ok: true, response: json, persisted: Boolean(token)};
    } catch (e) {
        return {ok: false, error: e instanceof Error ? e.message : 'Unknown error'};
    }
}


