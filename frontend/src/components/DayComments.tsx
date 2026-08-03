"use client";

import React, {useActionState, useCallback, useEffect, useId, useRef, useState} from "react";
import Link from "next/link";
import Form from "next/form";
import {useFormStatus} from "react-dom";
import useSWR from "swr";
import {ArchiveIcon, GameControllerIcon, PaperPlaneRightIcon} from "@phosphor-icons/react/ssr";
import {useAuth} from "@/contexts/AuthContext";
import {buildSteamLoginUrl} from "@/components/SteamLoginButton";
import ReactionBar from "@/components/ReactionBar";
import {
    COMMENT_MODERATOR_STEAM_ID,
    archiveComment,
    commentsUrl,
    fetchComments,
    searchMentionCandidates,
    steamStoreUrl,
    type CommentGameRef,
    type DayComment,
    type MentionCandidate,
} from "@/lib/comments";
import {formatRelativeTime} from "@/lib/format";
import {useDebouncedValue} from "@/lib/hooks/useDebouncedValue";
import {nextOpenPickerId} from "@/lib/reactionPicker";
import type {RoundResult, StoredDay} from "@/lib/storage";
import {Routes} from "../../app/routes";
import {
    postCommentAction,
    type CommentActionState,
} from "../../app/review-guesser/comments/actions";
import "@/styles/components/dayComments.css";

const MAX_BODY_LENGTH = 1000;
const MIN_MENTION_QUERY_LENGTH = 2;
const MENTION_DEBOUNCE_MS = 300;

const initialActionState: CommentActionState = {ok: false};

/** Markdown game refs from chips, plus bare Steam store URLs. */
const GAME_LINK_RE =
    /\[([^\]]+)]\((https:\/\/store\.steampowered\.com\/app\/\d+)\)|https:\/\/store\.steampowered\.com\/app\/\d+/g;

/** Markdown @mention tokens inserted by the composer's mention picker. */
const MENTION_RE = /\[(@[^\]]+)]\(mention:([^)]+)\)/g;

function sanitizeGameLinkLabel(name: string): string {
    return name.replace(/[\[\]]/g, "").trim() || "Steam game";
}

/** Strips markdown-breaking characters from a persona name used as a mention label. */
function sanitizeMentionLabel(name: string): string {
    return name.replace(/[\[\]]/g, "").trim() || "Player";
}

type BodyMatch = {
    index: number;
    length: number;
    node: React.ReactNode;
};

/**
 * Renders comment body text with Steam game refs and @mentions as links.
 */
export function CommentBodyText({body}: {body: string}): React.ReactElement {
    const matches: BodyMatch[] = [];

    for (const match of body.matchAll(GAME_LINK_RE)) {
        const index = match.index ?? 0;
        const label = match[1] ? sanitizeGameLinkLabel(match[1]) : match[0];
        const url = match[2] ?? match[0];
        matches.push({
            index,
            length: match[0].length,
            node: (
                <a
                    key={`game-${url}-${index}`}
                    href={url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="day-comments__game-link"
                    title={label}
                >
                    {label}
                </a>
            ),
        });
    }

    for (const match of body.matchAll(MENTION_RE)) {
        const index = match.index ?? 0;
        const label = sanitizeMentionLabel(match[1]);
        const steamId = match[2];
        matches.push({
            index,
            length: match[0].length,
            node: (
                <Link
                    key={`mention-${steamId}-${index}`}
                    href={Routes.profile(steamId)}
                    className="day-comments__mention-link"
                >
                    {label}
                </Link>
            ),
        });
    }

    matches.sort((a, b) => a.index - b.index);

    const nodes: React.ReactNode[] = [];
    let last = 0;
    for (const match of matches) {
        if (match.index < last) continue; // overlapping match, keep the earlier one
        if (match.index > last) nodes.push(body.slice(last, match.index));
        nodes.push(match.node);
        last = match.index + match.length;
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
            {!pending && <PaperPlaneRightIcon size={16} weight="bold" aria-hidden="true"/>}
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
 * Scans left from the caret for an active, unclosed `@query` mention token.
 * Returns null when the caret isn't inside one (no '@' before whitespace, or
 * the '@' is part of a word like an email address).
 */
function detectMentionQuery(value: string, caret: number): {start: number; query: string} | null {
    const upToCaret = value.slice(0, caret);
    const atIndex = upToCaret.lastIndexOf("@");
    if (atIndex === -1) return null;
    const query = upToCaret.slice(atIndex + 1);
    if (/\s/.test(query)) return null;
    const charBeforeAt = atIndex > 0 ? upToCaret[atIndex - 1] : "";
    if (/\w/.test(charBeforeAt)) return null;
    return {start: atIndex, query};
}

/**
 * Replaces an in-progress `@query` token with a mention reference at the textarea caret.
 */
function insertMentionReference(
    textarea: HTMLTextAreaElement,
    candidate: MentionCandidate,
    mentionStart: number,
    maxLength: number,
): number | null {
    const label = sanitizeMentionLabel(candidate.personaName);
    const snippet = `[@${label}](mention:${candidate.steamId})`;
    const caret = textarea.selectionStart ?? textarea.value.length;
    const before = textarea.value.slice(0, mentionStart);
    const after = textarea.value.slice(caret);
    const padAfter = after.length > 0 && !/^\s/.test(after) ? " " : "";
    const insertion = `${snippet}${padAfter}`;
    const next = before + insertion + after;
    if (next.length > maxLength) return null;
    textarea.value = next;
    const nextCaret = (before + insertion).length;
    textarea.focus();
    textarea.setSelectionRange(nextCaret, nextCaret);
    return next.length;
}

/**
 * Compact trigger that opens today's game picks for inserting Steam links.
 */
function GameLinkPicker(props: {
    games: CommentGameRef[];
    onPick: (game: CommentGameRef) => void;
}): React.ReactElement {
    const {games, onPick} = props;
    const [open, setOpen] = useState(false);
    const rootRef = useRef<HTMLDivElement>(null);
    const pickerId = useId();

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

    return (
        <div className="comment-composer__game-picker" ref={rootRef}>
            <button
                type="button"
                className={`comment-composer__game-trigger${open ? " comment-composer__game-trigger--open" : ""}`}
                aria-haspopup="menu"
                aria-expanded={open}
                aria-controls={pickerId}
                title="Insert a game link"
                aria-label="Insert a game link"
                onClick={() => setOpen((value) => !value)}
            >
                <GameControllerIcon size={18} weight="regular" aria-hidden="true"/>
                <span className="comment-composer__game-trigger-plus" aria-hidden="true">+</span>
            </button>
            {open && (
                <div
                    id={pickerId}
                    className="comment-composer__game-menu"
                    role="menu"
                    aria-label="Today's games"
                >
                    {games.map((game) => {
                        const label = game.name || `App ${game.appId}`;
                        return (
                            <button
                                key={game.appId}
                                type="button"
                                role="menuitem"
                                className="comment-composer__game-option"
                                title={label}
                                aria-label={label}
                                onClick={() => {
                                    onPick(game);
                                    setOpen(false);
                                }}
                            >
                                {label}
                            </button>
                        );
                    })}
                </div>
            )}
        </div>
    );
}

/**
 * Suggestion dropdown for the @mention autocomplete, anchored below the composer's textarea.
 * Closes on outside-click or Escape.
 */
function MentionMenu(props: {
    id: string;
    candidates: MentionCandidate[];
    onPick: (candidate: MentionCandidate) => void;
    onClose: () => void;
}): React.ReactElement {
    const {id, candidates, onPick, onClose} = props;
    const rootRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        const onPointerDown = (event: MouseEvent | TouchEvent) => {
            const target = event.target as Node | null;
            if (rootRef.current && target && !rootRef.current.contains(target)) {
                onClose();
            }
        };
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key === "Escape") onClose();
        };

        document.addEventListener("mousedown", onPointerDown);
        document.addEventListener("touchstart", onPointerDown);
        document.addEventListener("keydown", onKeyDown);
        return () => {
            document.removeEventListener("mousedown", onPointerDown);
            document.removeEventListener("touchstart", onPointerDown);
            document.removeEventListener("keydown", onKeyDown);
        };
    }, [onClose]);

    return (
        <div
            id={id}
            ref={rootRef}
            className="comment-composer__mention-menu"
            role="listbox"
            aria-label="Mention suggestions"
        >
            {candidates.map((candidate) => (
                <button
                    key={candidate.steamId}
                    type="button"
                    role="option"
                    aria-selected="false"
                    className="comment-composer__mention-option"
                    title={candidate.personaName}
                    onClick={() => onPick(candidate)}
                >
                    {candidate.avatar && (
                        <img
                            className="comment-composer__mention-avatar"
                            src={candidate.avatar}
                            alt=""
                            width={20}
                            height={20}
                            loading="lazy"
                            referrerPolicy="no-referrer"
                        />
                    )}
                    <span>{candidate.personaName}</span>
                </button>
            ))}
        </div>
    );
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
    const {isSignedIn, steamId} = useAuth();
    const formRef = useRef<HTMLFormElement>(null);
    const textareaRef = useRef<HTMLTextAreaElement>(null);
    const [posted, setPosted] = useState(false);
    const [bodyLength, setBodyLength] = useState(0);
    const [state, formAction] = useActionState(postCommentAction, initialActionState);
    const lastHandled = useRef<CommentActionState | null>(null);
    const remaining = MAX_BODY_LENGTH - bodyLength;
    const mentionMenuId = useId();

    const [mention, setMention] = useState<{start: number; query: string} | null>(null);
    const [mentionCandidates, setMentionCandidates] = useState<MentionCandidate[]>([]);
    const debouncedMentionQuery = useDebouncedValue(mention?.query ?? null, MENTION_DEBOUNCE_MS);

    useEffect(() => {
        if (!state || state === lastHandled.current) return;
        lastHandled.current = state;

        if (state.ok) {
            formRef.current?.reset();
            setBodyLength(0);
            setMention(null);
            setMentionCandidates([]);
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

    useEffect(() => {
        if (isSignedIn !== true || debouncedMentionQuery === null
            || debouncedMentionQuery.trim().length < MIN_MENTION_QUERY_LENGTH) {
            setMentionCandidates([]);
            return;
        }
        let cancelled = false;
        void searchMentionCandidates(debouncedMentionQuery.trim()).then((candidates) => {
            if (cancelled) return;
            setMentionCandidates(candidates.filter((candidate) => candidate.steamId !== steamId));
        });
        return () => {
            cancelled = true;
        };
    }, [isSignedIn, debouncedMentionQuery, steamId]);

    const handleInsertGame = (game: CommentGameRef) => {
        const ta = textareaRef.current;
        if (!ta) return;
        const nextLength = insertGameReference(ta, game, MAX_BODY_LENGTH);
        if (nextLength !== null) setBodyLength(nextLength);
    };

    const closeMentionMenu = useCallback(() => {
        setMention(null);
        setMentionCandidates([]);
    }, []);

    const handlePickMention = (candidate: MentionCandidate) => {
        const ta = textareaRef.current;
        if (!ta || !mention) return;
        const nextLength = insertMentionReference(ta, candidate, mention.start, MAX_BODY_LENGTH);
        if (nextLength !== null) setBodyLength(nextLength);
        closeMentionMenu();
    };

    const handleBodyInput = (e: React.FormEvent<HTMLTextAreaElement>) => {
        const value = e.currentTarget.value;
        setBodyLength(value.length);
        const caret = e.currentTarget.selectionStart ?? value.length;
        setMention(detectMentionQuery(value, caret));
    };

    const mentionOpen = isSignedIn === true && mention !== null && mentionCandidates.length > 0;

    return (
        <Form ref={formRef} className="comment-composer" action={formAction}>
            <input type="hidden" name="gameDate" value={gameDate}/>
            <label className="comment-composer__label" htmlFor={`day-comment-${gameDate}`}>
                Your comment
            </label>
            <div className="comment-composer__input-wrap">
                <textarea
                    ref={textareaRef}
                    id={`day-comment-${gameDate}`}
                    className="comment-composer__input"
                    name="body"
                    maxLength={MAX_BODY_LENGTH}
                    placeholder="Share your take on today's games… Type @ to mention someone."
                    required
                    aria-expanded={mentionOpen}
                    aria-controls={mentionOpen ? mentionMenuId : undefined}
                    onInput={handleBodyInput}
                />
                {mentionOpen && (
                    <MentionMenu
                        id={mentionMenuId}
                        candidates={mentionCandidates}
                        onPick={handlePickMention}
                        onClose={closeMentionMenu}
                    />
                )}
            </div>
            <div className="comment-composer__actions">
                {games.length > 0 && (
                    <GameLinkPicker games={games} onPick={handleInsertGame}/>
                )}
                <span
                    className={`comment-composer__count${remaining <= 40 ? " comment-composer__count--warn" : ""}`}
                    title="Characters remaining"
                    aria-live="polite"
                >
                    {remaining} chars left
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
 * @param props.totalRounds - Total rounds for this day; with `latestRound`/`latest`/`results`,
 * used to hide comments from signed-in players until they finish the day (readOnly is exempt).
 * @returns The comments section, or `null` when no game date is provided, or when a signed-in
 * player has not yet completed all rounds for this day.
 */
export default function DayComments(props: {
    gameDate?: string;
    games?: CommentGameRef[];
    readOnly?: boolean;
    totalRounds?: number;
    latestRound?: number;
    latest?: RoundResult;
    results?: Record<number, RoundResult>;
}): React.ReactElement | null {
    const {gameDate, games, readOnly = false, totalRounds, latestRound, latest, results} = props;
    const {isSignedIn, steamId, refreshAuth} = useAuth();
    const canModerate = isSignedIn === true && steamId === COMMENT_MODERATOR_STEAM_ID;
    const [archivingId, setArchivingId] = useState<number | null>(null);
    const [openPickerCommentId, setOpenPickerCommentId] = useState<number | null>(null);

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

    // Comments unlock for signed-in players only once they've finished every
    // round for this day (same completion check as RoundSummary/ShareControls).
    // Readonly (archive) views and anonymous visitors are exempt from this gate.
    if (!readOnly && isSignedIn === true && totalRounds !== undefined && latestRound !== undefined && latest) {
        let stored: StoredDay | null = null;
        try {
            const raw = typeof window !== "undefined" ? window.localStorage.getItem(`review-guesser:${gameDate}`) : null;
            stored = raw ? (JSON.parse(raw) as StoredDay) : null;
        } catch {
            stored = null;
        }
        const merged: Record<number, RoundResult> = {
            ...(stored?.results || {}),
            ...(results || {}),
            [latestRound]: latest,
        };
        const isComplete = Object.keys(merged).length >= totalRounds;
        if (!isComplete) return null;
    }

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
                    {readOnly
                        ? "No comments for this day."
                        : "No comments yet. Be the first to share a take — strong reactions can earn a Player Spotlight."}
                </p>
            )}

            {data && data.length > 0 && (
                <ul className={`day-comments__list${openPickerCommentId !== null ? " day-comments__list--picker-open" : ""}`}>
                    {data.map((comment) => {
                        const author = comment.author;
                        const displayName = author.personaName || author.steamId;
                        const relative = comment.createdAt ? formatRelativeTime(comment.createdAt) : "";
                        return (
                            <li
                                key={comment.id}
                                className={`day-comments__item${openPickerCommentId === comment.id ? " day-comments__item--picker-open" : ""}`}
                            >
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
                                            open={openPickerCommentId === comment.id}
                                            onPickerOpenChange={(isOpen) => {
                                                setOpenPickerCommentId((prev) => nextOpenPickerId(prev, comment.id, isOpen));
                                            }}
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
