"use client";

import React, {useState} from "react";
import {buildSteamLoginUrl} from "@/components/SteamLoginButton";
import {
    REACTION_EMOJI,
    REACTION_TYPES,
    type CommentReactionDto,
    type ReactionType,
    toggleReaction,
} from "@/lib/comments";
import "@/styles/components/dayComments.css";

/**
 * Displays reaction buttons for a comment and handles reaction toggles.
 *
 * @param props.commentId - The ID of the comment whose reactions are being changed.
 * @param props.reactions - The comment's current reaction data.
 * @param props.canReact - Whether the viewer is allowed to react.
 * @param props.onToggled - Callback invoked after a reaction is successfully changed.
 * @returns A grouped set of reaction buttons.
 */
export default function ReactionBar(props: {
    commentId: number;
    reactions: CommentReactionDto[];
    canReact: boolean;
    onToggled: () => void | Promise<unknown>;
}) {
    const {commentId, reactions, canReact, onToggled} = props;
    const [pending, setPending] = useState<ReactionType | null>(null);

    const byType = new Map<string, CommentReactionDto>();
    for (const reaction of reactions) {
        byType.set(reaction.reactionType, reaction);
    }

    const handleToggle = async (reactionType: ReactionType) => {
        if (pending) return;
        if (!canReact) {
            window.location.href = buildSteamLoginUrl();
            return;
        }
        setPending(reactionType);
        try {
            await toggleReaction(commentId, reactionType);
            await onToggled();
        } catch {
            // Keep current UI; SWR will retain prior data.
        } finally {
            setPending(null);
        }
    };

    return (
        <div className="reaction-bar" role="group" aria-label="Reactions">
            {REACTION_TYPES.map((type) => {
                const entry = byType.get(type);
                const count = entry?.count ?? 0;
                const active = Boolean(entry?.reactedByViewer);
                const isPending = pending === type;
                return (
                    <button
                        key={type}
                        type="button"
                        className={[
                            "reaction-bar__button",
                            active ? "reaction-bar__button--active" : "",
                            isPending ? "reaction-bar__button--pending" : "",
                        ].filter(Boolean).join(" ")}
                        disabled={pending !== null}
                        aria-pressed={active}
                        aria-busy={isPending}
                        title={canReact ? undefined : "Sign in to react"}
                        aria-label={`${REACTION_EMOJI[type]} reaction${count ? `, ${count}` : ""}${canReact ? "" : " (sign in to react)"}`}
                        onClick={() => handleToggle(type)}
                    >
                        <span className="reaction-bar__emoji" aria-hidden="true">
                            {REACTION_EMOJI[type]}
                        </span>
                        {count > 0 && <span className="reaction-bar__count">{count}</span>}
                    </button>
                );
            })}
        </div>
    );
}
