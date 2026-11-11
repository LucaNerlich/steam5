"use client";

import {useActionState, useEffect, useState, useTransition} from "react";
import type {GuessActionState} from "../../app/review-guesser/[round]/actions";
import {submitGuessAction} from "../../app/review-guesser/[round]/actions";
import AuthWarningModal from "@/components/AuthWarningModal";
import DayComments from "@/components/DayComments";
import useAuthWarningGate from "@/components/round/useAuthWarningGate";
import useStoredDay, {notifyStoredDayChanged} from "@/components/round/useStoredDay";
import GuessSubmissionCard from "@/components/round/GuessSubmissionCard";
import RoundResultSection from "@/components/round/RoundResultSection";
import {useAuth} from "@/contexts/AuthContext";
import type {CommentGameRef} from "@/lib/comments";
import useServerGuesses from "@/lib/hooks/useServerGuesses";
import useRoundArrowNavigation from "@/lib/hooks/useRoundArrowNavigation";
import {saveRound, type RoundResult} from "@/lib/storage";
import {resolveSubmissionTarget, type RoundSubmission} from "@/lib/guessResolution";
import {deriveRoundPlay} from "@/lib/roundPlay";
import {validatedResultsForDay} from "@/lib/rounds";
import {Routes} from "../../app/routes";
import "@/styles/components/reviewGuesserRound.css";
import "@/styles/components/reviewRoundResult.css";
import "@/styles/components/reviewShareControls.css";

interface Props {
    appId: number;
    buckets: string[];
    bucketTitles?: string[];
    roundIndex: number;
    totalRounds: number;
    pickName?: string;
    gameDate: string;
    /** Today's picks for comment quick-link chips. */
    dayGames?: CommentGameRef[];
}

type StoredRoundResult = RoundResult;

// Round indexes present in a results record, collected in a single pass
// (parseInt + finiteness check fused into one loop instead of .map().filter()).
function numericRoundKeys(record: Record<number, StoredRoundResult>): number[] {
    const keys: number[] = [];
    for (const key of Object.keys(record)) {
        const parsed = parseInt(key, 10);
        if (Number.isFinite(parsed)) keys.push(parsed);
    }
    return keys;
}

/**
 * Renders the guessing interface and result view for a review round.
 *
 * @param appId - The identifier of the current game.
 * @param buckets - The available review buckets.
 * @param bucketTitles - Display titles for the review buckets.
 * @param roundIndex - The current round number.
 * @param totalRounds - The total number of rounds in the game.
 * @param pickName - The name of the current pick.
 * @param gameDate - The date identifying the game.
 * @returns The rendered guessing controls, round result, and related actions.
 */
export default function ReviewGuesserRound({
                                                appId,
                                                buckets,
                                                bucketTitles,
                                                roundIndex,
                                                totalRounds,
                                                pickName,
                                                gameDate,
                                                dayGames
                                            }: Props) {
    const initial: GuessActionState = {ok: false};
    const [state, formAction] = useActionState<GuessActionState, FormData>(submitGuessAction, initial);
    const [isPending, startTransition] = useTransition();
    const [selectedLabel, setSelectedLabel] = useState<string | null>(null);
    // The identity of the guess that is currently in flight, captured at submit time.
    // The label travelled with the live `selectedLabel` before, so navigating away
    // mid-submit (which clears the selection) dropped a genuinely graded response:
    // it rendered but was never persisted, leaving the round re-guessable after a
    // reload. Capturing gameDate/round/appId/label atomically keeps the save honest.
    const [submission, setSubmission] = useState<RoundSubmission | null>(null);
    const {guesses: serverGuesses, loading: serverGuessesLoading} = useServerGuesses();

    const scopeKey = `${gameDate ?? ''}:${roundIndex}:${appId}`;

    // @view-transition { navigation: auto } in globals.css overrides Next.js's
    // default scroll-to-top on client navigations, so we reset manually.
    useEffect(() => {
        window.scrollTo({ top: 0, behavior: "instant" });
    }, [roundIndex]);

    // Reset the fresh selection when the round scope changes. This guarded
    // render-phase update is React's documented pattern for adjusting state
    // when a prop changes; it converges because the previous-scope marker is
    // updated in the same branch. An in-flight submission is deliberately kept —
    // it must still persist for the round it was made on when it resolves.
    const [prevScopeKey, setPrevScopeKey] = useState<string | null>(null);
    if (prevScopeKey !== scopeKey) {
        setPrevScopeKey(scopeKey);
        if (selectedLabel !== null) setSelectedLabel(null);
    }

    const nextRound = roundIndex + 1;
    const nextHref = nextRound <= totalRounds ? `/review-guesser/${nextRound}` : `/review-guesser/1`;

    const prevRound = roundIndex - 1;
    const prevHref = prevRound >= 1 ? `/review-guesser/${prevRound}` : null;
    const hasNextRound = roundIndex < totalRounds;

    // Persist this round's result for the current game date, then notify the
    // stored-day external store so readers re-render with the fresh snapshot.
    useEffect(() => {
        const target = resolveSubmissionTarget(state, submission);
        if (!target) return;
        saveRound(target.gameDate, target.roundIndex, target.totalRounds, target.result);
        notifyStoredDayChanged();
        setSubmission(null);
    }, [state, submission]);

    // Live mirror of the stored day in localStorage (see useStoredDay).
    const stored = useStoredDay(gameDate);

    // Determine completion and existing result for this round.
    // serverGuesses already arrives in StoredRoundResult shape from the hook.
    const storedResults = stored?.results || {};
    const serverResults: Record<number, StoredRoundResult> = {...serverGuesses};
    // A stored/server result is only valid for this round if it belongs to the
    // current pick. When today's picks are regenerated, results keyed by round
    // index would otherwise surface a previous game's guess on the new game.
    const isForCurrentPick = (r?: StoredRoundResult) => !!r && r.appId === appId;
    const storedThisRound = isForCurrentPick(storedResults[roundIndex])
        ? storedResults[roundIndex]
        : (isForCurrentPick(serverResults[roundIndex]) ? serverResults[roundIndex] : undefined);

    // Determine auth state (client-side) to conditionally show the sign-in nudge.
    const {isSignedIn, refreshAuth} = useAuth();

    const submitGuess = (formData: FormData) => {
        // Capture the whole submission identity (also for the deferred modal paths) so the
        // resolving response is persisted for the round actually guessed, even when the
        // player has moved on and the round-scope reset has cleared the live selection.
        const label = formData.get('bucketGuess');
        if (typeof label === 'string' && label) {
            setSubmission({gameDate, roundIndex, totalRounds, appId, pickName, label});
        }
        startTransition(() => {
            formAction(formData);
        });
    };

    // Must run before deriveRoundPlay: the pre-submit auth probe is a pending phase.
    const {
        showAuthWarning,
        isGating,
        handleAuthGuardedSubmit,
        handleLogin,
        handleSkip,
        handleIgnore
    } = useAuthWarningGate(isSignedIn, refreshAuth, submitGuess);

    // The stored label wins over a live selection: after a round change the live
    // selection is cleared, but the stored result still names the guessed bucket.
    const selectedForRound = storedThisRound?.selectedLabel ?? selectedLabel;

    // One explicit phase for the round view; see lib/roundPlay.ts. It replaces the
    // former mix of isPending/isGating/submitted/error booleans, so "submitted with
    // no result" and similar combinations can no longer be rendered.
    const play = deriveRoundPlay({
        appId,
        actionState: state,
        stored: storedThisRound,
        selection: selectedForRound,
        pending: isPending || isGating,
        signedIn: isSignedIn,
    });
    const effectiveResponse = play.phase === "graded" ? play.response : null;
    const signedOutDuringPlay = play.signedOut;

    // Merge the "current" submitted/effective response into server results so
    // authenticated users see completion immediately without relying on SSR re-fetch
    const mergedServerResults: Record<number, StoredRoundResult> = {...serverResults};
    if (effectiveResponse) {
        mergedServerResults[roundIndex] = {
            appId,
            pickName, // preserve current round's name
            selectedLabel: selectedForRound ?? '',
            actualBucket: effectiveResponse.actualBucket,
            totalReviews: effectiveResponse.totalReviews,
            correct: effectiveResponse.correct,
        };
    }

    const latestStoredRoundIndex = (() => {
        const keys = [...numericRoundKeys(storedResults), ...numericRoundKeys(mergedServerResults)];
        if (keys.length === 0) return roundIndex;
        return Math.max(...keys);
    })();
    const latestStored = storedResults[latestStoredRoundIndex] || mergedServerResults[latestStoredRoundIndex];
    const latestResult = (Object.keys(mergedServerResults).length > 0
        ? mergedServerResults[latestStoredRoundIndex]
        : null) ||
        latestStored ||
        {
            appId,
            pickName,
            selectedLabel: (selectedForRound ?? '') as string,
            actualBucket: effectiveResponse ? effectiveResponse.actualBucket : (storedThisRound?.actualBucket ?? ''),
            totalReviews: effectiveResponse ? effectiveResponse.totalReviews : (storedThisRound?.totalReviews ?? 0),
            correct: effectiveResponse ? effectiveResponse.correct : (storedThisRound?.correct ?? false),
        };

    // Single source of truth: when to show ShareControls
    const hasServerResults = Object.keys(serverResults).length > 0;
    const canShowShare = Boolean(effectiveResponse) || (!serverGuessesLoading && hasServerResults);

    // Detect a silently-lost session: either the backend rejected our cookie (401),
    // or the UI believed we were signed in but the guess was saved anonymously
    // (cookie was dropped while the SPA stayed open). In both cases the result did
    // not count, so re-sync the auth state and surface a clear notice.
    useEffect(() => {
        if (signedOutDuringPlay) refreshAuth();
    }, [signedOutDuringPlay, refreshAuth]);

    useRoundArrowNavigation({prevHref, nextHref, hasNextRound, disabled: showAuthWarning});

    // A result only counts toward completion when it belongs to one of today's picks:
    // after a regenerated pick set, round-index keys from the old games must not unlock
    // comments or leak into the shared score.
    // Validate each round index against the pick served for that round. Membership in the
    // set is not enough: a stale result at index 2 for a game that is now round 3 would still
    // pass, and the consumers only count keys.
    const validatedResults = validatedResultsForDay(dayGames, storedResults, mergedServerResults, totalRounds);
    const validatedLatestIndex = Object.keys(validatedResults).reduce((max, key) => Math.max(max, Number(key)), 0);
    const latestRoundForShare = validatedLatestIndex || latestStoredRoundIndex;
    const latestForShare = validatedLatestIndex > 0 ? validatedResults[validatedLatestIndex] : latestResult;
    const shareResults = !serverGuessesLoading && Object.keys(validatedResults).length > 0
        ? validatedResults
        : undefined;
    return (
        <>
            {play.phase !== "graded" && (
                <GuessSubmissionCard
                    appId={appId}
                    buckets={buckets}
                    bucketTitles={bucketTitles}
                    selectedLabel={play.selection}
                    onSelect={setSelectedLabel}
                    isPending={play.phase === "submitting"}
                    formAction={handleAuthGuardedSubmit}
                    signedOutDuringPlay={signedOutDuringPlay}
                    error={play.phase === "rejected" ? play.error : undefined}
                />
            )}

            {play.phase === "graded" && (
                <RoundResultSection
                    appId={appId}
                    buckets={buckets}
                    selectedLabel={play.selection}
                    result={play.response}
                    prevHref={prevHref}
                    nextHref={roundIndex < totalRounds ? nextHref : null}
                    randomArchiveHref={roundIndex >= totalRounds ? Routes.randomArchive : null}
                    canShowShare={canShowShare}
                    gameDate={gameDate}
                    totalRounds={totalRounds}
                    latestRound={latestRoundForShare}
                    latest={latestForShare}
                    shareResults={shareResults}
                    hasServerResults={hasServerResults}
                    signedIn={isSignedIn}
                    signedOutDuringPlay={signedOutDuringPlay}
                />
            )}

            {/* Comments are public for every visitor; posting/reacting still requires sign-in,
                and signed-in players only see them once they finish all of today's rounds. */}
            <DayComments
                gameDate={gameDate}
                games={dayGames}
                totalRounds={totalRounds}
                latestRound={latestRoundForShare}
                latest={latestForShare}
                results={shareResults}
            />

            <AuthWarningModal
                isOpen={showAuthWarning}
                onLogin={handleLogin}
                onSkip={handleSkip}
                onIgnore={handleIgnore}
            />
        </>
    );
}
