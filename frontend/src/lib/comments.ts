export type ReactionType = "THUMBS_UP" | "LAUGH_CRYING" | "LAUGH_TEAR" | "HUG";

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
export async function fetchComments(gameDate: string): Promise<DayComment[]> {
    const res = await fetch(commentsUrl(gameDate), {cache: "no-store"});
    if (!res.ok) {
        throw new Error("Failed to fetch comments");
    }
    return res.json();
}

/**
 * Creates a comment for a game date.
 *
 * @param gameDate - The game date associated with the comment
 * @param body - The comment text
 * @returns The created comment
 */
export async function postComment(gameDate: string, body: string): Promise<DayComment> {
    const res = await fetch(commentsUrl(gameDate), {
        method: "POST",
        headers: {"content-type": "application/json", accept: "application/json"},
        body: JSON.stringify({body}),
        cache: "no-store",
    });
    if (!res.ok) {
        const err = await res.json().catch(() => ({}));
        throw new Error(err?.error || "Failed to post comment");
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
        const err = await res.json().catch(() => ({}));
        throw new Error(err?.error || "Failed to toggle reaction");
    }
    return res.json();
}
