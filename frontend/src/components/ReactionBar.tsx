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
 * Builds a tooltip string listing reactor names, appending an overflow note
 * (e.g. "and 3 more") when the total count exceeds the returned names.
 * Returns undefined when there are no resolved reactor names to show.
 */
function reactorTooltip(count: number, reactors: string[]): string | undefined {
    if (reactors.length === 0) return undefined;
    const remaining = count - reactors.length;
    const names = reactors.join(", ");
    return remaining > 0 ? `${names} and ${remaining} more` : names;
}

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
    onUnauthorized?: () => void;
    /** When true, show reaction counts only — no picker or toggle controls. */
    readOnly?: boolean;
    /** Whether the picker is open. Owned by the parent so only one picker across the list is ever open. */
    open?: boolean;
    /** Requests that the picker be opened or closed. */
    onPickerOpenChange?: (open: boolean) => void;
}) {
    const {
        commentId, reactions, canReact, onToggled, onUnauthorized, readOnly = false,
        open = false, onPickerOpenChange,
    } = props;
    const [pending, setPending] = useState<ReactionType | null>(null);
    const rootRef = useRef<HTMLDivElement>(null);
    const pickerId = useId();

    // Kept in a ref so the outside-click/Escape effect below only re-subscribes
    // when `open` changes, not on every parent render (the callback is
    // typically a fresh closure), and so unmount cleanup calls the latest one.
    const onPickerOpenChangeRef = useRef(onPickerOpenChange);
    useEffect(() => {
        onPickerOpenChangeRef.current = onPickerOpenChange;
    }, [onPickerOpenChange]);

    useEffect(() => {
        return () => onPickerOpenChangeRef.current?.(false);
    }, []);

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
                reactors: entry?.reactors ?? [],
            };
        })
        .filter((row): row is {type: ReactionType; count: number; active: boolean; reactors: string[]} => row !== null);

    useEffect(() => {
        if (!open) return;

        const onPointerDown = (event: MouseEvent | TouchEvent) => {
            const target = event.target as Node | null;
            if (rootRef.current && target && !rootRef.current.contains(target)) {
                onPickerOpenChangeRef.current?.(false);
            }
        };
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === "Escape") onPickerOpenChangeRef.current?.(false);
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
        if (readOnly || pending) return;
        if (!canReact) {
            window.location.href = buildSteamLoginUrl();
            return;
        }
        setPending(reactionType);
        try {
            await toggleReaction(commentId, reactionType);
            await onToggled();
            onPickerOpenChange?.(false);
        } catch (e) {
            const message = e instanceof Error ? e.message : "";
            if (
                message === "unauthorized"
                || message === "unauthenticated"
                || message === "Unauthorized"
            ) {
                onUnauthorized?.();
                return;
            }
            // Keep current UI; SWR will retain prior data.
        } finally {
            setPending(null);
        }
    };

    const handleOpenPicker = () => {
        if (readOnly) return;
        if (!canReact) {
            window.location.href = buildSteamLoginUrl();
            return;
        }
        onPickerOpenChange?.(!open);
    };

    if (readOnly) {
        if (present.length === 0) return null;
        return (
            <div className="reaction-bar" ref={rootRef}>
                <div className="reaction-bar__summary" aria-label="Reactions">
                    {present.map(({type, count, reactors}) => (
                        <span
                            key={type}
                            className="reaction-bar__chip"
                            aria-label={`${REACTION_EMOJI[type]} reaction, ${count}`}
                            title={reactorTooltip(count, reactors)}
                        >
                            <span className="reaction-bar__emoji" aria-hidden="true">
                                {REACTION_EMOJI[type]}
                            </span>
                            <span className="reaction-bar__count">{count}</span>
                        </span>
                    ))}
                </div>
            </div>
        );
    }

    return (
        <div className="reaction-bar" ref={rootRef}>
            {present.length > 0 && (
                <div className="reaction-bar__summary" aria-label="Reactions">
                    {present.map(({type, count, active, reactors}) => {
                        const actionHint = canReact ? (active ? "Remove reaction" : "Add reaction") : "Sign in to react";
                        const tooltip = reactorTooltip(count, reactors);
                        return (
                            <button
                                key={type}
                                type="button"
                                className={`reaction-bar__chip${active ? " reaction-bar__chip--active" : ""}`}
                                disabled={pending !== null}
                                aria-pressed={active}
                                title={tooltip ? `${actionHint} — ${tooltip}` : actionHint}
                                aria-label={`${REACTION_EMOJI[type]} reaction, ${count}${canReact ? "" : " (sign in to react)"}`}
                                onClick={() => handleToggle(type)}
                            >
                                <span className="reaction-bar__emoji" aria-hidden="true">
                                    {REACTION_EMOJI[type]}
                                </span>
                                <span className="reaction-bar__count">{count}</span>
                            </button>
                        );
                    })}
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
