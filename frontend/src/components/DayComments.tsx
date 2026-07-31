"use client";

import React, {useState} from "react";
import Link from "next/link";
import useSWR from "swr";
import {useAuth} from "@/contexts/AuthContext";
import {buildSteamLoginUrl} from "@/components/SteamLoginButton";
import ReactionBar from "@/components/ReactionBar";
import {
    commentsUrl,
    fetchComments,
    postComment,
    type DayComment,
} from "@/lib/comments";
import {resolveLiveSignedIn} from "@/lib/authGuard";
import {loadDay, type RoundResult} from "@/lib/storage";
import "@/styles/components/dayComments.css";

const MAX_BODY_LENGTH = 1000;

function initialsFor(name: string | null | undefined): string {
    if (!name) return "?";
    const trimmed = name.trim();
    if (!trimmed) return "?";
    return trimmed.charAt(0).toUpperCase();
}

async function fetchSignedIn(): Promise<boolean> {
    try {
        const r = await fetch("/api/auth/me", {cache: "no-store"});
        if (r.status === 401) return false;
        if (!r.ok) return true;
        const data = await r.json();
        return Boolean(data?.signedIn);
    } catch {
        return true;
    }
}

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
            alt={displayName}
            title={displayName}
            width={32}
            height={32}
            loading="lazy"
            referrerPolicy="no-referrer"
        />
    ) : (
        <span className="day-comments__avatar" title={displayName} aria-label={displayName}>
            {initialsFor(personaName)}
        </span>
    );
    return (
        <Link href={profileUrl} className="day-comments__avatar-link" aria-label={`View ${displayName}'s profile`}>
            {content}
        </Link>
    );
}

function isDayComplete(props: {
    gameDate?: string;
    totalRounds: number;
    latestRound: number;
    latest: RoundResult;
    results?: Record<number, RoundResult>;
}): boolean {
    const {gameDate, totalRounds, latestRound, latest, results} = props;
    if (!gameDate) return false;
    const stored = loadDay(gameDate);
    const merged: Record<number, RoundResult> = {
        ...(stored?.results || {}),
        ...(results || {}),
        [latestRound]: latest,
    };
    return Object.keys(merged).length >= totalRounds;
}

export default function DayComments(props: {
    gameDate?: string;
    totalRounds: number;
    latestRound: number;
    latest: {
        appId: number;
        pickName?: string;
        selectedLabel: string;
        actualBucket: string;
        totalReviews: number;
        correct: boolean;
    };
    results?: Record<number, {
        pickName?: string;
        appId: number;
        selectedLabel: string;
        actualBucket: string;
        totalReviews: number;
        correct: boolean;
    }>;
}) {
    const {gameDate, totalRounds, latestRound, latest, results} = props;
    const {isSignedIn, refreshAuth} = useAuth();
    const [body, setBody] = useState("");
    const [submitting, setSubmitting] = useState(false);
    const [posted, setPosted] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const complete = isDayComplete({gameDate, totalRounds, latestRound, latest, results});
    const swrKey = complete && gameDate ? commentsUrl(gameDate) : null;
    const {data, error: loadError, isLoading, mutate} = useSWR<DayComment[]>(
        swrKey,
        () => fetchComments(gameDate as string),
        {revalidateOnFocus: false},
    );

    if (!gameDate || !complete) return null;

    const handleSubmit = async (event: React.FormEvent) => {
        event.preventDefault();
        const trimmed = body.trim();
        if (!trimmed || submitting) return;
        if (trimmed.length > MAX_BODY_LENGTH) {
            setError(`Comments must be ${MAX_BODY_LENGTH} characters or fewer.`);
            return;
        }

        const fetched = isSignedIn === false ? false : await fetchSignedIn();
        const live = resolveLiveSignedIn(isSignedIn, fetched);
        if (!live) {
            refreshAuth();
            setError("Sign in with Steam to post a comment.");
            return;
        }

        setSubmitting(true);
        setError(null);
        try {
            await postComment(gameDate, trimmed);
            setBody("");
            setPosted(true);
            window.setTimeout(() => setPosted(false), 1500);
            await mutate();
        } catch (e) {
            setError(e instanceof Error ? e.message : "Failed to post comment");
            refreshAuth();
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <section className="day-comments" aria-label="Day comments">
            <h3 className="day-comments__title">Comments</h3>

            {isLoading && <p className="day-comments__status">Loading comments…</p>}
            {loadError && <p className="day-comments__status">Could not load comments.</p>}
            {!isLoading && !loadError && (!data || data.length === 0) && (
                <p className="day-comments__empty">No comments yet. Be the first to share a take.</p>
            )}

            {data && data.length > 0 && (
                <ul className="day-comments__list">
                    {data.map((comment) => {
                        const author = comment.author;
                        const displayName = author.personaName || author.steamId;
                        return (
                            <li key={comment.id} className="day-comments__item">
                                <CommentAvatar
                                    steamId={author.steamId}
                                    personaName={displayName}
                                    avatar={author.avatar}
                                />
                                <div className="day-comments__body">
                                    <Link
                                        href={`/profile/${author.steamId}`}
                                        className="day-comments__author"
                                    >
                                        {displayName}
                                    </Link>
                                    <p className="day-comments__text">{comment.body}</p>
                                    <ReactionBar
                                        commentId={comment.id}
                                        reactions={comment.reactions}
                                        canReact={isSignedIn === true}
                                        onToggled={() => mutate()}
                                    />
                                </div>
                            </li>
                        );
                    })}
                </ul>
            )}

            {isSignedIn === true ? (
                <form className="comment-composer" onSubmit={handleSubmit}>
                    <textarea
                        className="comment-composer__input"
                        value={body}
                        onChange={(e) => setBody(e.target.value)}
                        maxLength={MAX_BODY_LENGTH}
                        placeholder="Share your take on today's games…"
                        aria-label="Comment"
                        disabled={submitting}
                    />
                    <div className="comment-composer__actions">
                        <span className={`comment-composer__posted ${posted ? "is-visible" : ""}`}>
                            Posted
                        </span>
                        <button
                            type="submit"
                            className="btn btn-cta comment-composer__submit"
                            disabled={submitting || body.trim().length === 0}
                        >
                            {submitting ? "Posting…" : "Post"}
                        </button>
                    </div>
                    {error && <p className="comment-composer__error">{error}</p>}
                </form>
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
                    {" "}to leave a comment.
                </p>
            )}
        </section>
    );
}
