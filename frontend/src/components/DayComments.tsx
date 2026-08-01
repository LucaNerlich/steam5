"use client";

import React, {useActionState, useCallback, useEffect, useRef, useState} from "react";
import Link from "next/link";
import Form from "next/form";
import {useFormStatus} from "react-dom";
import useSWR from "swr";
import {ArchiveIcon} from "@phosphor-icons/react/ssr";
import {useAuth} from "@/contexts/AuthContext";
import {buildSteamLoginUrl} from "@/components/SteamLoginButton";
import ReactionBar from "@/components/ReactionBar";
import {
    COMMENT_MODERATOR_STEAM_ID,
    archiveComment,
    commentsUrl,
    fetchComments,
    steamStoreUrl,
    type CommentGameRef,
    type DayComment,
} from "@/lib/comments";
import {formatRelativeTime} from "@/lib/format";
import {
    postCommentAction,
    type CommentActionState,
} from "../../app/review-guesser/comments/actions";
import "@/styles/components/dayComments.css";

const MAX_BODY_LENGTH = 1000;

const initialActionState: CommentActionState = {ok: false};

/** Markdown game refs from chips, plus bare Steam store URLs. */
const GAME_LINK_RE =
    /\[([^\]]+)]\((https:\/\/store\.steampowered\.com\/app\/\d+)\)|https:\/\/store\.steampowered\.com\/app\/\d+/g;

function sanitizeGameLinkLabel(name: string): string {
    return name.replace(/[\[\]]/g, "").trim() || "Steam game";
}

/**
 * Renders comment body text with Steam game refs as named links.
 */
function CommentBodyText({body}: {body: string}): React.ReactElement {
    const nodes: React.ReactNode[] = [];
    let last = 0;
    for (const match of body.matchAll(GAME_LINK_RE)) {
        const index = match.index ?? 0;
        if (index > last) nodes.push(body.slice(last, index));
        const label = match[1] ? sanitizeGameLinkLabel(match[1]) : match[0];
        const url = match[2] ?? match[0];
        nodes.push(
            <a
                key={`${url}-${index}`}
                href={url}
                target="_blank"
                rel="noopener noreferrer"
                className="day-comments__game-link"
                title={label}
            >
                {label}
            </a>,
        );
        last = index + match[0].length;
    }
    if (last < body.length) nodes.push(body.slice(last));
    return <>{nodes}</>;
}

/**
 * Gets the uppercase initial for a name.
 *
 * @param name - The name to extract an initial from
 * @returns The uppercase first character of the trimmed name, or `"?"` when the name is missing or blank
 */
function initialsFor(name: string | null | undefined): string {
    if (!name) return "?";
    const trimmed = name.trim();
    if (!trimmed) return "?";
    return trimmed.charAt(0).toUpperCase();
}

/**
 * Renders a profile link with a user's avatar or name initials fallback.
 *
 * @param props - The user's Steam ID, display name, and optional avatar URL.
 */
function CommentAvatar(props: {
    steamId: string;
    personaName: string;
    avatar: string | null;
}): React.ReactElement {
    const {steamId, personaName, avatar} = props;
    const displayName = personaName || "Player";
    const profileUrl = `/profile/${steamId}`;
    const content = avatar ? (
        <img
            className="day-comments__avatar"
            src={avatar}
            alt=""
            title={displayName}
            width={32}
            height={32}
            loading="lazy"
            referrerPolicy="no-referrer"
        />
    ) : (
        <span className="day-comments__avatar" title={displayName} aria-hidden="true">
            {initialsFor(personaName)}
        </span>
    );
    return (
        <Link href={profileUrl} className="day-comments__avatar-link" aria-label={`View ${displayName}'s profile`}>
            {content}
        </Link>
    );
}

/**
 * Renders a comment submission button that indicates when submission is in progress.
 *
 * @param disabled - When true, the button cannot be clicked (e.g. empty body).
 * @returns The comment submission button.
 */
function CommentSubmitButton({disabled}: {disabled: boolean}): React.ReactElement {
    const {pending} = useFormStatus();
    return (
        <button
            type="submit"
            className="btn btn-cta comment-composer__submit"
            disabled={pending || disabled}
        >
            {pending ? "Posting…" : "Post"}
        </button>
    );
}

/**
 * Inserts a Steam store reference for a day's game at the textarea caret.
 */
function insertGameReference(
    textarea: HTMLTextAreaElement,
    game: CommentGameRef,
    maxLength: number,
): number | null {
    const label = sanitizeGameLinkLabel(game.name);
    const snippet = `[${label}](${steamStoreUrl(game.appId)})`;
    const start = textarea.selectionStart ?? textarea.value.length;
    const end = textarea.selectionEnd ?? start;
    const before = textarea.value.slice(0, start);
    const after = textarea.value.slice(end);
    const padBefore = before.length > 0 && !/\s$/.test(before) ? " " : "";
    const padAfter = after.length > 0 && !/^\s/.test(after) ? " " : "";
    const insertion = `${padBefore}${snippet}${padAfter}`;
    const next = before + insertion + after;
    if (next.length > maxLength) return null;
    textarea.value = next;
    const caret = (before + insertion).length;
    textarea.focus();
    textarea.setSelectionRange(caret, caret);
    return next.length;
}

/**
 * Renders a form for submitting a comment associated with a game date.
 *
 * @param props - The game date and callbacks for successful or unauthorized submissions.
 */
function CommentComposer(props: {
    gameDate: string;
    games?: CommentGameRef[];
    onPosted: () => void | Promise<unknown>;
    onUnauthorized: () => void;
}): React.ReactElement {
    const {gameDate, games = [], onPosted, onUnauthorized} = props;
    const formRef = useRef<HTMLFormElement>(null);
    const textareaRef = useRef<HTMLTextAreaElement>(null);
    const [posted, setPosted] = useState(false);
    const [bodyLength, setBodyLength] = useState(0);
    const [state, formAction] = useActionState(postCommentAction, initialActionState);
    const lastHandled = useRef<CommentActionState | null>(null);
    const remaining = MAX_BODY_LENGTH - bodyLength;

    useEffect(() => {
        if (!state || state === lastHandled.current) return;
        lastHandled.current = state;

        if (state.ok) {
            formRef.current?.reset();
            setBodyLength(0);
            setPosted(true);
            const timer = window.setTimeout(() => setPosted(false), 1500);
            void onPosted();
            return () => window.clearTimeout(timer);
        }
        if (state.outcomeUnknown) {
            // Refresh so a comment that may have landed is visible before any re-submit.
            void onPosted();
        }
        if (state.unauthorized) {
            onUnauthorized();
        }
    }, [state, onPosted, onUnauthorized]);

    const handleInsertGame = (game: CommentGameRef) => {
        const ta = textareaRef.current;
        if (!ta) return;
        const nextLength = insertGameReference(ta, game, MAX_BODY_LENGTH);
        if (nextLength !== null) setBodyLength(nextLength);
    };

    return (
        <Form ref={formRef} className="comment-composer" action={formAction}>
            <input type="hidden" name="gameDate" value={gameDate}/>
            <label className="comment-composer__label" htmlFor={`day-comment-${gameDate}`}>
                Your comment
            </label>
            {games.length > 0 && (
                <div className="comment-composer__games" aria-label="Insert a game link">
                    {games.map((game) => {
                        const label = game.name || `App ${game.appId}`;
                        return (
                            <button
                                key={game.appId}
                                type="button"
                                className="comment-composer__game-chip"
                                title={label}
                                aria-label={label}
                                onClick={() => handleInsertGame(game)}
                            >
                                {label}
                            </button>
                        );
                    })}
                </div>
            )}
            <textarea
                ref={textareaRef}
                id={`day-comment-${gameDate}`}
                className="comment-composer__input"
                name="body"
                maxLength={MAX_BODY_LENGTH}
                placeholder="Share your take on today's games…"
                required
                onInput={(e) => setBodyLength(e.currentTarget.value.length)}
            />
            <div className="comment-composer__actions">
                <span
                    className={`comment-composer__count${remaining <= 40 ? " comment-composer__count--warn" : ""}`}
                    aria-live="polite"
                >
                    {remaining}
                </span>
                <span className={`comment-composer__posted ${posted ? "is-visible" : ""}`}>
                    Posted
                </span>
                <CommentSubmitButton disabled={bodyLength === 0}/>
            </div>
            {state && !state.ok && state.error && (
                <p className="comment-composer__error" role="alert">{state.error}</p>
            )}
        </Form>
    );
}

/**
 * Displays comments and comment interactions for a game date.
 *
 * @param props - Component properties.
 * @param props.gameDate - The game date whose comments are displayed.
 * @param props.games - Today's picks for quick Steam-link chips in the composer.
 * @param props.readOnly - When true, shows the comment list only (no composer or react).
 * @returns The comments section, or `null` when no game date is provided.
 */
export default function DayComments(props: {
    gameDate?: string;
    games?: CommentGameRef[];
    readOnly?: boolean;
}): React.ReactElement | null {
    const {gameDate, games, readOnly = false} = props;
    const {isSignedIn, steamId, refreshAuth} = useAuth();
    const canModerate = isSignedIn === true && steamId === COMMENT_MODERATOR_STEAM_ID;
    const [archivingId, setArchivingId] = useState<number | null>(null);

    const swrKey = gameDate ? commentsUrl(gameDate) : null;
    // Long browser cache only for anonymous archive views (no reactedByViewer).
    const immutableFetch = readOnly && isSignedIn !== true;
    const {data, error: loadError, isLoading, mutate} = useSWR<DayComment[]>(
        swrKey,
        () => fetchComments(gameDate as string, {immutable: immutableFetch}),
        readOnly && !canModerate
            ? {
                revalidateOnFocus: false,
                revalidateOnReconnect: false,
                revalidateIfStale: false,
            }
            : {revalidateOnFocus: false},
    );

    const handlePosted = useCallback(() => {
        void mutate();
    }, [mutate]);

    const handleUnauthorized = useCallback(() => {
        refreshAuth();
    }, [refreshAuth]);

    const handleArchive = useCallback(async (commentId: number) => {
        if (archivingId !== null) return;
        setArchivingId(commentId);
        try {
            await archiveComment(commentId);
            await mutate();
        } catch (e) {
            const message = e instanceof Error ? e.message : "";
            if (message === "unauthorized") {
                refreshAuth();
            }
        } finally {
            setArchivingId(null);
        }
    }, [archivingId, mutate, refreshAuth]);

    if (!gameDate) return null;

    const commentCount = data?.length ?? 0;
    const canReact = !readOnly && isSignedIn === true;

    return (
        <section className="day-comments" aria-label="Day comments">
            <div className="day-comments__header">
                <h3 className="day-comments__title">Comments</h3>
                {commentCount > 0 && (
                    <span className="day-comments__count" aria-label={`${commentCount} comments`}>
                        {commentCount}
                    </span>
                )}
            </div>

            {isLoading && <p className="day-comments__status">Loading comments…</p>}
            {loadError && <p className="day-comments__status">Could not load comments.</p>}
            {!isLoading && !loadError && commentCount === 0 && (
                <p className="day-comments__empty">
                    {readOnly ? "No comments for this day." : "No comments yet. Be the first to share a take."}
                </p>
            )}

            {data && data.length > 0 && (
                <ul className="day-comments__list">
                    {data.map((comment) => {
                        const author = comment.author;
                        const displayName = author.personaName || author.steamId;
                        const relative = comment.createdAt ? formatRelativeTime(comment.createdAt) : "";
                        return (
                            <li key={comment.id} className="day-comments__item">
                                <CommentAvatar
                                    steamId={author.steamId}
                                    personaName={displayName}
                                    avatar={author.avatar}
                                />
                                <div className="day-comments__body">
                                    <div className="day-comments__top">
                                        <div className="day-comments__meta">
                                            <Link
                                                href={`/profile/${author.steamId}`}
                                                className="day-comments__author"
                                            >
                                                {displayName}
                                            </Link>
                                            {relative && (
                                                <time
                                                    className="day-comments__time"
                                                    dateTime={comment.createdAt ?? undefined}
                                                    title={comment.createdAt ?? undefined}
                                                >
                                                    {relative}
                                                </time>
                                            )}
                                            {canModerate && (
                                                <button
                                                    type="button"
                                                    className="day-comments__archive"
                                                    title="Archive comment"
                                                    aria-label="Archive comment"
                                                    disabled={archivingId !== null}
                                                    onClick={() => void handleArchive(comment.id)}
                                                >
                                                    <ArchiveIcon size={14} weight="regular" aria-hidden="true"/>
                                                    <span>{archivingId === comment.id ? "…" : "Archive"}</span>
                                                </button>
                                            )}
                                        </div>
                                        <ReactionBar
                                            commentId={comment.id}
                                            reactions={comment.reactions}
                                            canReact={canReact}
                                            readOnly={readOnly}
                                            onToggled={handlePosted}
                                            onUnauthorized={handleUnauthorized}
                                        />
                                    </div>
                                    <p className="day-comments__text">
                                        <CommentBodyText body={comment.body}/>
                                    </p>
                                </div>
                            </li>
                        );
                    })}
                </ul>
            )}

            {!readOnly && (isSignedIn === true ? (
                <CommentComposer
                    gameDate={gameDate}
                    games={games}
                    onPosted={handlePosted}
                    onUnauthorized={handleUnauthorized}
                />
            ) : (
                <p className="day-comments__signin">
                    <button
                        type="button"
                        className="btn-link"
                        onClick={() => {
                            window.location.href = buildSteamLoginUrl();
                        }}
                    >
                        Sign in with Steam
                    </button>
                    {" "}to leave a comment or react.
                </p>
            ))}
        </section>
    );
}
