'use server';

import {cookies} from 'next/headers';

const MAX_BODY_LENGTH = 1000;

export type CommentActionState = {
    ok: boolean;
    error?: string;
    unauthorized?: boolean;
    commentId?: number;
};

/**
 * Submits a day comment using the server-side session.
 *
 * @param formData - Form data containing the game date and comment body
 * @returns The submission status, optional error message, and created comment ID on success
 */
export async function postCommentAction(
    _prev: CommentActionState | undefined,
    formData: FormData,
): Promise<CommentActionState> {
    const gameDateRaw = formData.get('gameDate');
    const bodyRaw = formData.get('body');

    const gameDate = typeof gameDateRaw === 'string' ? gameDateRaw.trim() : '';
    const body = typeof bodyRaw === 'string' ? bodyRaw.trim() : '';

    if (!gameDate || !/^\d{4}-\d{2}-\d{2}$/.test(gameDate)) {
        return {ok: false, error: 'Invalid date'};
    }
    if (!body) {
        return {ok: false, error: 'Comment cannot be empty'};
    }
    if (body.length > MAX_BODY_LENGTH) {
        return {ok: false, error: `Comments must be ${MAX_BODY_LENGTH} characters or fewer.`};
    }

    const token = (await cookies()).get('s5_token')?.value;
    if (!token) {
        return {ok: false, unauthorized: true, error: 'Sign in with Steam to post a comment.'};
    }

    try {
        const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
        const res = await fetch(
            `${backend}/api/review-game/comments/${encodeURIComponent(gameDate)}`,
            {
                method: 'POST',
                headers: {
                    'content-type': 'application/json',
                    accept: 'application/json',
                    authorization: `Bearer ${token}`,
                },
                body: JSON.stringify({body}),
                cache: 'no-store',
            },
        );

        if (res.status === 401) {
            return {ok: false, unauthorized: true, error: 'Sign in with Steam to post a comment.'};
        }
        if (!res.ok) {
            const err = await res.json().catch(() => ({})) as {error?: string};
            const code = err?.error;
            if (code === 'day_not_complete') {
                return {ok: false, error: 'Finish all rounds for this day before commenting.'};
            }
            if (code === 'rate_limit_exceeded') {
                return {ok: false, error: 'Too many comments — try again in a minute.'};
            }
            if (code === 'body_too_long') {
                return {ok: false, error: `Comments must be ${MAX_BODY_LENGTH} characters or fewer.`};
            }
            return {ok: false, error: code || `Upstream error ${res.status}`};
        }

        const json = await res.json() as {id?: number};
        return {ok: true, commentId: typeof json.id === 'number' ? json.id : undefined};
    } catch (e) {
        console.error('postCommentAction failed', e);
        return {ok: false, error: 'Something went wrong. Please try again.'};
    }
}
