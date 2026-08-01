import React from "react";
import Link from "next/link";
import {BACKEND_ORIGIN as backend} from "@/lib/backend";
import {REACTION_EMOJI, type DayComment, type ReactionType} from "@/lib/comments";
import "@/styles/components/commentSpotlight.css";

type CommentHighlightResponse = {
    gameDate: string;
    totalReactions: number;
    comment: DayComment;
};

const BODY_PREVIEW_MAX = 160;

function truncateBody(body: string): string {
    const trimmed = body.trim().replace(/\s+/g, " ");
    if (trimmed.length <= BODY_PREVIEW_MAX) return trimmed;
    return `${trimmed.slice(0, BODY_PREVIEW_MAX - 1).trimEnd()}…`;
}

function topReactionEmoji(comment: DayComment): string {
    let bestType: ReactionType | null = null;
    let bestCount = 0;
    for (const reaction of comment.reactions ?? []) {
        const type = reaction.reactionType as ReactionType;
        if (!(type in REACTION_EMOJI)) continue;
        if (reaction.count > bestCount) {
            bestCount = reaction.count;
            bestType = type;
        }
    }
    return bestType ? REACTION_EMOJI[bestType] : "💬";
}

async function loadYesterdayHighlight(): Promise<CommentHighlightResponse | null> {
    try {
        const res = await fetch(`${backend}/api/review-game/comments/highlight/yesterday`, {
            headers: {accept: "application/json"},
            next: {revalidate: 300, tags: ["comment-highlight-yesterday"]},
            signal: AbortSignal.timeout(3000),
        });
        if (res.status === 204) return null;
        if (!res.ok) {
            console.error(`CommentSpotlight: unexpected ${res.status} from highlight endpoint`);
            return null;
        }
        return await res.json();
    } catch (err) {
        console.error("CommentSpotlight: failed to load highlight", err);
        return null;
    }
}

/**
 * Surfaces yesterday's most-reacted day comment on round 1 — social proof that
 * encourages players to leave and react to comments.
 */
export default async function CommentSpotlight(): Promise<React.ReactElement | null> {
    const highlight = await loadYesterdayHighlight();
    if (!highlight?.comment?.author) return null;

    const {comment, totalReactions, gameDate} = highlight;
    const author = comment.author;
    const displayName = author.personaName || author.steamId || "Player";
    const emoji = topReactionEmoji(comment);
    const preview = truncateBody(comment.body || "");
    if (!preview) return null;

    const reactionLabel = totalReactions === 1 ? "1 reaction" : `${totalReactions} reactions`;

    return (
        <aside className="comment-spotlight" aria-label="Comment spotlight">
            <p className="comment-spotlight__eyebrow">Comment spotlight</p>
            <p className="comment-spotlight__headline">
                <span className="comment-spotlight__emoji" aria-hidden="true">{emoji}</span>
                {" "}
                <Link href={`/profile/${encodeURIComponent(author.steamId)}`}>
                    <strong>{displayName}</strong>
                </Link>
                {" "}led yesterday with {reactionLabel}
            </p>
            <p className="comment-spotlight__quote">“{preview}”</p>
            <p className="comment-spotlight__detail">
                <Link href={`/review-guesser/archive/${encodeURIComponent(gameDate)}`}>
                    See yesterday&apos;s comments
                </Link>
                {" · leave a take after you finish today"}
            </p>
        </aside>
    );
}
