"use client";

import React, {useActionState, useCallback, useEffect, useRef, useState} from "react";
import Link from "next/link";
import Form from "next/form";
import {useFormStatus} from "react-dom";
import useSWR from "swr";
import {useAuth} from "@/contexts/AuthContext";
import {buildSteamLoginUrl} from "@/components/SteamLoginButton";
import ReactionBar from "@/components/ReactionBar";
import {
    commentsUrl,
    fetchComments,
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

<<<<<<< HEAD
/**
 * Renders a comment submission button that indicates when submission is in progress.
 *
 * @returns The comment submission button.
 */
function CommentSubmitButton(): React.ReactElement {
=======
function CommentSubmitButton({disabled}: {disabled: boolean}): React.ReactElement {
>>>>>>> e011771 (Polish day comments section UX and styling)
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
 * Renders a form for submitting a comment associated with a game date.
 *
 * @param props - The game date and callbacks for successful or unauthorized submissions.
 */
function CommentComposer(props: {
    gameDate: string;
    onPosted: () => void | Promise<unknown>;
    onUnauthorized: () => void;
}): React.ReactElement {
    const {gameDate, onPosted, onUnauthorized} = props;
    const formRef = useRef<HTMLFormElement>(null);
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
        if (state.unauthorized) {
            onUnauthorized();
        }
    }, [state, onPosted, onUnauthorized]);

    return (
        <Form ref={formRef} className="comment-composer" action={formAction}>
            <input type="hidden" name="gameDate" value={gameDate}/>
            <label className="comment-composer__label" htmlFor={`day-comment-${gameDate}`}>
                Your comment
            </label>
            <textarea
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
 * @returns The comments section, or `null` when no game date is provided.
 */
export default function DayComments(props: {
    gameDate?: string;
}): React.ReactElement | null {
    const {gameDate} = props;
    const {isSignedIn, refreshAuth} = useAuth();

    const swrKey = gameDate ? commentsUrl(gameDate) : null;
    const {data, error: loadError, isLoading, mutate} = useSWR<DayComment[]>(
        swrKey,
        () => fetchComments(gameDate as string),
        {revalidateOnFocus: false},
    );

    const handlePosted = useCallback(() => {
        void mutate();
    }, [mutate]);

    const handleUnauthorized = useCallback(() => {
        refreshAuth();
    }, [refreshAuth]);

    if (!gameDate) return null;

    const commentCount = data?.length ?? 0;

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
                <p className="day-comments__empty">No comments yet. Be the first to share a take.</p>
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
                                    </div>
                                    <p className="day-comments__text">{comment.body}</p>
                                    <ReactionBar
                                        commentId={comment.id}
                                        reactions={comment.reactions}
                                        canReact={isSignedIn === true}
                                        onToggled={handlePosted}
                                    />
                                </div>
                            </li>
                        );
                    })}
                </ul>
            )}

            {isSignedIn === true ? (
                <CommentComposer
                    gameDate={gameDate}
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
            )}
        </section>
    );
}
