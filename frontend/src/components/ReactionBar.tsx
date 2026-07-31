"use client";

import React, {useState} from "react";
import {
    REACTION_EMOJI,
    REACTION_TYPES,
    type CommentReactionDto,
    type ReactionType,
    toggleReaction,
} from "@/lib/comments";
import "@/styles/components/dayComments.css";

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
        if (!canReact || pending) return;
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
                return (
                    <button
                        key={type}
                        type="button"
                        className={`reaction-bar__button${active ? " reaction-bar__button--active" : ""}`}
                        disabled={!canReact || pending !== null}
                        aria-pressed={active}
                        aria-label={`${REACTION_EMOJI[type]} reaction${count ? `, ${count}` : ""}`}
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
