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
    // Archive / past days advertise long Cache-Control; allow the browser to honor it.
    // Live day stays no-store so newly posted comments show up promptly.
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
        const err = await res.json().catch(() => ({})) as {error?: string; message?: string};
        if (
            res.status === 401
            || err?.error === "unauthenticated"
            || err?.error === "Unauthorized"
        ) {
            throw new Error("unauthorized");
        }
        const code = (typeof err?.message === "string" && /^[a-z][a-z0-9_]*$/.test(err.message))
            ? err.message
            : err?.error;
        throw new Error(code || "Failed to toggle reaction");
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
        const err = await res.json().catch(() => ({})) as {error?: string; message?: string};
        if (
            res.status === 401
            || err?.error === "unauthenticated"
            || err?.error === "Unauthorized"
        ) {
            throw new Error("unauthorized");
        }
        const code = (typeof err?.message === "string" && /^[a-z][a-z0-9_]*$/.test(err.message))
            ? err.message
            : err?.error;
        throw new Error(code || "Failed to archive comment");
    }
}
