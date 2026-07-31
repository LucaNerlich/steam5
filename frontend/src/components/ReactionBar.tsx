"use client";

import React, {useEffect, useId, useRef, useState} from "react";
import {SmileyIcon} from "@phosphor-icons/react/ssr";
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
 * Compact reaction summary plus a single picker control for a comment.
 *
 * Shows only reactions that already have a count; a smile button opens the
 * four-emoji picker. Signed-out viewers can see counts and are sent to Steam
 * login when they try to react.
 */
export default function ReactionBar(props: {
    commentId: number;
    reactions: CommentReactionDto[];
    canReact: boolean;
    onToggled: () => void | Promise<unknown>;
}) {
    const {commentId, reactions, canReact, onToggled} = props;
    const [pending, setPending] = useState<ReactionType | null>(null);
    const [open, setOpen] = useState(false);
    const rootRef = useRef<HTMLDivElement>(null);
    const pickerId = useId();

    const byType = new Map<string, CommentReactionDto>();
    for (const reaction of reactions) {
        byType.set(reaction.reactionType, reaction);
    }

    const present = REACTION_TYPES
        .map((type) => {
            const entry = byType.get(type);
            const count = entry?.count ?? 0;
            if (count <= 0) return null;
            return {
                type,
                count,
                active: Boolean(entry?.reactedByViewer),
            };
        })
        .filter((row): row is {type: ReactionType; count: number; active: boolean} => row !== null);

    useEffect(() => {
        if (!open) return;

        const onPointerDown = (event: MouseEvent | TouchEvent) => {
            const target = event.target as Node | null;
            if (rootRef.current && target && !rootRef.current.contains(target)) {
                setOpen(false);
            }
        };
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === "Escape") setOpen(false);
        };

        document.addEventListener("mousedown", onPointerDown);
        document.addEventListener("touchstart", onPointerDown);
        document.addEventListener("keydown", onKeyDown);
        return () => {
            document.removeEventListener("mousedown", onPointerDown);
            document.removeEventListener("touchstart", onPointerDown);
            document.removeEventListener("keydown", onKeyDown);
        };
    }, [open]);

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
            setOpen(false);
        } catch {
            // Keep current UI; SWR will retain prior data.
        } finally {
            setPending(null);
        }
    };

    const handleOpenPicker = () => {
        if (!canReact) {
            window.location.href = buildSteamLoginUrl();
            return;
        }
        setOpen((value) => !value);
    };

    return (
        <div className="reaction-bar" ref={rootRef}>
            {present.length > 0 && (
                <div className="reaction-bar__summary" aria-label="Reactions">
                    {present.map(({type, count, active}) => (
                        <button
                            key={type}
                            type="button"
                            className={`reaction-bar__chip${active ? " reaction-bar__chip--active" : ""}`}
                            disabled={pending !== null}
                            aria-pressed={active}
                            title={canReact ? (active ? "Remove reaction" : "Add reaction") : "Sign in to react"}
                            aria-label={`${REACTION_EMOJI[type]} reaction, ${count}${canReact ? "" : " (sign in to react)"}`}
                            onClick={() => handleToggle(type)}
                        >
                            <span className="reaction-bar__emoji" aria-hidden="true">
                                {REACTION_EMOJI[type]}
                            </span>
                            <span className="reaction-bar__count">{count}</span>
                        </button>
                    ))}
                </div>
            )}

            <div className="reaction-bar__picker-wrap">
                <button
                    type="button"
                    className={`reaction-bar__trigger${open ? " reaction-bar__trigger--open" : ""}`}
                    aria-haspopup="menu"
                    aria-expanded={open}
                    aria-controls={pickerId}
                    title={canReact ? "Add a reaction" : "Sign in to react"}
                    aria-label={canReact ? "Add a reaction" : "Sign in to react"}
                    disabled={pending !== null}
                    onClick={handleOpenPicker}
                >
                    <SmileyIcon size={18} weight="regular" aria-hidden="true"/>
                    <span className="reaction-bar__trigger-plus" aria-hidden="true">+</span>
                </button>

                {open && (
                    <div
                        id={pickerId}
                        className="reaction-bar__picker"
                        role="menu"
                        aria-label="Choose a reaction"
                    >
                        {REACTION_TYPES.map((type) => {
                            const entry = byType.get(type);
                            const active = Boolean(entry?.reactedByViewer);
                            const isPending = pending === type;
                            return (
                                <button
                                    key={type}
                                    type="button"
                                    role="menuitemcheckbox"
                                    aria-checked={active}
                                    className={[
                                        "reaction-bar__option",
                                        active ? "reaction-bar__option--active" : "",
                                        isPending ? "reaction-bar__option--pending" : "",
                                    ].filter(Boolean).join(" ")}
                                    disabled={pending !== null}
                                    onClick={() => handleToggle(type)}
                                >
                                    <span className="reaction-bar__emoji" aria-hidden="true">
                                        {REACTION_EMOJI[type]}
                                    </span>
                                </button>
                            );
                        })}
                    </div>
                )}
            </div>
        </div>
    );
}
