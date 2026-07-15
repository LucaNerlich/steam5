'use server';

import type {GuessResponse, HintResponse} from "@/types/year-game";
import {cookies} from 'next/headers';

export type GuessActionState = {
    ok: boolean;
    error?: string;
    response?: GuessResponse;
    unauthorized?: boolean;
    persisted?: boolean;
};

export type HintActionState = {
    ok: boolean;
    error?: string;
    response?: HintResponse;
    unauthorized?: boolean;
};

export async function submitYearGuessAction(
    _prev: GuessActionState | undefined,
    formData: FormData,
): Promise<GuessActionState> {
    const appIdRaw = formData.get('appId');
    const guessYearRaw = formData.get('guessYear');

    const appId = typeof appIdRaw === 'string' ? Number.parseInt(appIdRaw, 10) : NaN;
    const guessYear = typeof guessYearRaw === 'string' ? Number.parseInt(guessYearRaw, 10) : NaN;
    const hintsUsedRaw = formData.get('hintsUsed');
    const knownHintsUsed = typeof hintsUsedRaw === 'string' ? Number.parseInt(hintsUsedRaw, 10) : 0;
    if (!Number.isFinite(appId) || !Number.isFinite(guessYear) || guessYear < 1970 || guessYear > 2100) {
        return {ok: false, error: 'Enter a valid year'};
    }

    try {
        const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
        const token = (await cookies()).get('s5_token')?.value;
        if (knownHintsUsed > 0 && !token) {
            return {ok: false, unauthorized: true, error: 'Sign in to submit after using hints'};
        }
        const url = token ? `${backend}/api/year-game/guess-auth` : `${backend}/api/year-game/guess`;
        const headers: Record<string, string> = {'content-type': 'application/json', 'accept': 'application/json'};
        if (token) headers['authorization'] = `Bearer ${token}`;
        const res = await fetch(url, {
            method: 'POST',
            headers,
            body: JSON.stringify({
                appId,
                guessYear,
                clientHintsUsed: knownHintsUsed > 0 ? knownHintsUsed : undefined,
            }),
            cache: 'no-store',
        });
        if (!res.ok) {
            if (token && res.status === 401) {
                return {ok: false, unauthorized: true, error: 'unauthorized'};
            }
            return {ok: false, error: `Upstream error ${res.status}`};
        }
        const json: GuessResponse = await res.json();
        return {ok: true, response: json, persisted: Boolean(token)};
    } catch (e) {
        return {ok: false, error: e instanceof Error ? e.message : 'Unknown error'};
    }
}

export async function revealYearHintAction(
    _prev: HintActionState | undefined,
    formData: FormData,
): Promise<HintActionState> {
    const appIdRaw = formData.get('appId');
    const hintLevelRaw = formData.get('hintLevel');
    const appId = typeof appIdRaw === 'string' ? Number.parseInt(appIdRaw, 10) : NaN;
    const hintLevel = typeof hintLevelRaw === 'string' ? Number.parseInt(hintLevelRaw, 10) : NaN;
    if (!Number.isFinite(appId) || !Number.isFinite(hintLevel)) {
        return {ok: false, error: 'Invalid input'};
    }

    try {
        const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
        const token = (await cookies()).get('s5_token')?.value;
        if (!token) {
            return {ok: false, unauthorized: true, error: 'unauthorized'};
        }
        const res = await fetch(`${backend}/api/year-game/hint`, {
            method: 'POST',
            headers: {
                'content-type': 'application/json',
                'accept': 'application/json',
                'authorization': `Bearer ${token}`,
            },
            body: JSON.stringify({appId, hintLevel}),
            cache: 'no-store',
        });
        if (!res.ok) {
            if (res.status === 401) {
                return {ok: false, unauthorized: true, error: 'unauthorized'};
            }
            return {ok: false, error: `Upstream error ${res.status}`};
        }
        const json: HintResponse = await res.json();
        return {ok: true, response: json};
    } catch (e) {
        return {ok: false, error: e instanceof Error ? e.message : 'Unknown error'};
    }
}
