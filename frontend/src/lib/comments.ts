export type ReactionType = "THUMBS_UP" | "LAUGH_CRYING" | "LAUGH_TEAR" | "HUG";

/** Steam ID allowed to soft-archive comments (must match backend CommentModerator). */
export const COMMENT_MODERATOR_STEAM_ID = "76561198028075069";

export const REACTION_TYPES: ReactionType[] = [
    "THUMBS_UP",
    "LAUGH_CRYING",
    "LAUGH_TEAR",
    "HUG",
];

export const REACTION_EMOJI: Record<ReactionType, string> = {
    THUMBS_UP: "👍",
    LAUGH_CRYING: "😂",
    LAUGH_TEAR: "😢",
    HUG: "🤗",
};

export type CommentAuthor = {
    steamId: string;
    personaName: string;
    avatar: string | null;
    avatarBlurdata: string | null;
};

export type CommentReactionDto = {
    reactionType: ReactionType | string;
    count: number;
    reactedByViewer: boolean;
};

export type DayComment = {
    id: number;
    body: string;
    createdAt: string | null;
    author: CommentAuthor;
    reactions: CommentReactionDto[];
};

/** A day's pick shown as a quick-insert chip in the comment composer. */
export type CommentGameRef = {
    appId: number;
    name: string;
};

type ApiErrorBody = {
    error?: string;
    message?: string;
};

/**
 * Maps a failed comment-mutation response to a client Error.
 * Normalizes 401/unauthorized and prefers snake_case machine codes from ApiError.message.
 */
export function commentMutationError(
    status: number,
    body: ApiErrorBody,
    fallback: string,
): Error {
    if (
        status === 401
        || body?.error === "unauthenticated"
        || body?.error === "Unauthorized"
    ) {
        return new Error("unauthorized");
    }
    const code = (typeof body?.message === "string" && /^[a-z][a-z0-9_]*$/.test(body.message))
        ? body.message
        : body?.error;
    return new Error(code || fallback);
}

/** Steam store URL for a review-game pick. */
export function steamStoreUrl(appId: number): string {
    return `https://store.steampowered.com/app/${appId}`;
}

/**
 * Builds the API URL for comments associated with a game date.
 *
 * @param gameDate - The game date used to identify the comments
 * @returns The encoded comments API URL
 */
export function commentsUrl(gameDate: string): string {
    return `/api/review-game/comments/${encodeURIComponent(gameDate)}`;
}

/**
 * Fetches comments associated with a game date.
 *
 * @param gameDate - The game date used to identify the comments.
 * @returns The comments associated with the game date.
 */
export async function fetchComments(
    gameDate: string,
    options?: {immutable?: boolean},
): Promise<DayComment[]> {
    // Historical lists use a short revalidating Cache-Control; allow the browser to honor it.
    // Live / authenticated fetches stay no-store.
    const res = await fetch(commentsUrl(gameDate), {
        cache: options?.immutable ? "force-cache" : "no-store",
    });
    if (!res.ok) {
        throw new Error("Failed to fetch comments");
    }
    return res.json();
}

/**
 * Toggles a reaction on a comment.
 *
 * @param commentId - The identifier of the comment to update
 * @param reactionType - The reaction type to toggle
 * @returns The comment's updated reactions
 */
export async function toggleReaction(
    commentId: number,
    reactionType: ReactionType,
): Promise<CommentReactionDto[]> {
    const res = await fetch(
        `/api/review-game/comments/reactions/${encodeURIComponent(String(commentId))}`,
        {
            method: "POST",
            headers: {"content-type": "application/json", accept: "application/json"},
            body: JSON.stringify({reactionType}),
            cache: "no-store",
        },
    );
    if (!res.ok) {
        const err = await res.json().catch(() => ({})) as ApiErrorBody;
        throw commentMutationError(res.status, err, "Failed to toggle reaction");
    }
    return res.json();
}

/**
 * Soft-archives a comment (moderator only).
 */
export async function archiveComment(commentId: number): Promise<void> {
    const res = await fetch(
        `/api/review-game/comments/archive/${encodeURIComponent(String(commentId))}`,
        {
            method: "POST",
            headers: {accept: "application/json"},
            cache: "no-store",
        },
    );
    if (!res.ok) {
        const err = await res.json().catch(() => ({})) as ApiErrorBody;
        throw commentMutationError(res.status, err, "Failed to archive comment");
    }
}
