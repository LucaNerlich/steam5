'use server';

import type {GuessResponse} from "@/types/review-game";

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
        const res = await fetch(`${backend}/api/review-game/guess`, {
            method: 'POST',
            headers: {'content-type': 'application/json', 'accept': 'application/json'},
            body: JSON.stringify({appId, bucketGuess}),
            // Avoid caching mutations
            cache: 'no-store',
        });
        if (!res.ok) {
            return {ok: false, error: `Upstream error ${res.status}`};
        }
        const json: GuessResponse = await res.json();
        return {ok: true, response: json};
    } catch (e) {
        return {ok: false, error: e instanceof Error ? e.message : 'Unknown error'};
    }
}


