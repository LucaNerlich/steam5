'use server';

import type {GuessResponse, ReviewGameState} from "@/types/review-game";
import {cookies} from 'next/headers';

const MAX_BUCKET_GUESS_LENGTH = 50;

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

/** Loads today's allowed bucket labels from the backend; null when unavailable. */
async function loadTodayBuckets(backend: string): Promise<string[] | null> {
    try {
        const res = await fetch(`${backend}/api/review-game/today`, {
            headers: {accept: 'application/json'},
            next: {revalidate: 60, tags: ['round-today']},
            signal: AbortSignal.timeout(3000),
        });
        if (!res.ok) return null;
        const data: ReviewGameState = await res.json();
        return Array.isArray(data.buckets) ? data.buckets : null;
    } catch {
        return null;
    }
}

export async function submitGuessAction(_prev: GuessActionState | undefined, formData: FormData): Promise<GuessActionState> {
    const appIdRaw = formData.get('appId');
    const bucketGuess = formData.get('bucketGuess');

    // Accept only complete decimal strings — parseInt would silently truncate
    // prefixes like "10abc", "1.5", or "1e2" to 10/1 — and require a positive
    // safe integer so oversized values never reach the backend.
    const appId = typeof appIdRaw === 'string' && /^\d+$/.test(appIdRaw) ? Number(appIdRaw) : NaN;
    if (!Number.isSafeInteger(appId) || appId <= 0 || typeof bucketGuess !== 'string'
        || bucketGuess.length === 0 || bucketGuess.length > MAX_BUCKET_GUESS_LENGTH) {
        return {ok: false, error: 'Invalid input'};
    }

    try {
        const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
        // Reject values that are not one of today's bucket labels so arbitrary strings
        // never reach the backend (which would persist them verbatim and grade them
        // leniently). Fail closed when today's buckets cannot be loaded.
        const allowedBuckets = await loadTodayBuckets(backend);
        if (!allowedBuckets) {
            return {ok: false, error: 'Could not load today’s game — please try again'};
        }
        if (!allowedBuckets.includes(bucketGuess)) {
            return {ok: false, error: 'Invalid input'};
        }
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
        console.error('submitGuessAction failed', e);
        // Do not surface fetch internals (hosts, ports, connection errors) to the client.
        return {ok: false, error: 'Could not reach the game server — try again'};
    }
}


