"use client";

import {useActionState, useEffect, useMemo, useRef, useState, useTransition} from "react";
import type {HintTierMeta, MyYearGuess} from "@/types/year-game";
import type {GuessActionState, HintActionState} from "../../app/year-guesser/[round]/actions";
import {revealYearHintAction, submitYearGuessAction} from "../../app/year-guesser/[round]/actions";
import AuthWarningModal from "@/components/AuthWarningModal";
import RoundResultActions from "@/components/RoundResultActions";
import OtherPlayersNow from "@/components/OtherPlayersNow";
import {buildSteamLoginUrl} from "@/components/SteamLoginButton";
import {useAuth} from "@/contexts/AuthContext";
import useYearServerGuesses from "@/lib/hooks/useYearServerGuesses";
import useRoundArrowNavigation from "@/lib/hooks/useRoundArrowNavigation";
import {loadYearDay, saveYearRound, type RevealedHint, type YearRoundProgress} from "@/lib/yearStorage";
import {computeSignedOutDuringPlay, resolveLiveSignedIn, shouldWarnBeforeSubmit} from "@/lib/authGuard";
import "@/styles/components/yearGuesserHero.css";

interface Props {
    appId: number;
    hintTiers: HintTierMeta[];
    roundIndex: number;
    totalRounds: number;
    pickName?: string;
    gameDate?: string;
    serverGuess?: MyYearGuess;
}

const AUTH_WARNING_DISMISSED_COOKIE = "s5_auth_warning_dismissed";

function hasDismissedAuthWarning(): boolean {
    if (typeof document === "undefined") return false;
    return document.cookie.split("; ").some((cookie) => cookie === `${AUTH_WARNING_DISMISSED_COOKIE}=1`);
}

function dismissAuthWarning(): void {
    if (typeof document === "undefined") return;
    const oneYear = 365 * 24 * 60 * 60;
    document.cookie = `${AUTH_WARNING_DISMISSED_COOKIE}=1; path=/; max-age=${oneYear}; SameSite=Lax`;
}

async function fetchSignedIn(): Promise<boolean> {
    try {
        const response = await fetch('/api/auth/me', {cache: 'no-store'});
        if (response.status === 401) return false;
        if (!response.ok) return true;
        const data = await response.json();
        return Boolean(data?.signedIn);
    } catch {
        return true;
    }
}

function mergeProgress(
    local?: YearRoundProgress,
    server?: MyYearGuess,
): YearRoundProgress | undefined {
    if (!local && !server) return undefined;
    const base: YearRoundProgress = {
        appId: server?.appId ?? local?.appId ?? 0,
        pickName: local?.pickName,
        hintsUsed: server?.hintsUsed ?? local?.hintsUsed ?? 0,
        revealedHints: local?.revealedHints ?? [],
        unlockableHintLevels: server?.unlockableHintLevels ?? local?.unlockableHintLevels ?? [],
        lastDistance: local?.lastDistance ?? server?.bestDistance ?? undefined,
        lastGuessYear: server?.guessedYear ?? local?.lastGuessYear,
        completed: server?.completed ?? local?.completed ?? false,
        actualYear: server?.actualYear ?? local?.actualYear,
        points: server?.points ?? local?.points,
    };
    return base;
}

export default function YearGuesserRound({
    appId,
    hintTiers,
    roundIndex,
    totalRounds,
    pickName,
    gameDate,
    serverGuess,
}: Props) {
    const initialGuessState: GuessActionState = {ok: false};
    const initialHintState: HintActionState = {ok: false};
    const [guessState, guessAction] = useActionState<GuessActionState, FormData>(submitYearGuessAction, initialGuessState);
    const [hintState, hintAction] = useActionState<HintActionState, FormData>(revealYearHintAction, initialHintState);
    const [isGuessPending, startGuessTransition] = useTransition();
    const [isHintPending, startHintTransition] = useTransition();
    const [guessYearInput, setGuessYearInput] = useState("");
    const [progress, setProgress] = useState<YearRoundProgress | undefined>(() =>
        mergeProgress(
            gameDate ? loadYearDay(gameDate)?.rounds[roundIndex] : undefined,
            serverGuess,
        ),
    );
    const [showAuthWarning, setShowAuthWarning] = useState(false);
    const [pendingFormData, setPendingFormData] = useState<FormData | null>(null);
    const hintsRestoredRef = useRef(false);

    const disableClientFetch = Boolean(serverGuess?.completed);
    const {guesses: clientGuesses, loading: serverGuessesLoading} = useYearServerGuesses(disableClientFetch);

    const nextHref = useMemo(() => {
        const next = roundIndex + 1;
        return next <= totalRounds ? `/year-guesser/${next}` : `/year-guesser/1`;
    }, [roundIndex, totalRounds]);

    const prevHref = useMemo(() => {
        const prev = roundIndex - 1;
        return prev >= 1 ? `/year-guesser/${prev}` : null;
    }, [roundIndex]);

    useRoundArrowNavigation({
        prevHref,
        nextHref,
        hasNextRound: roundIndex < totalRounds,
        disabled: showAuthWarning,
    });

    useEffect(() => {
        window.scrollTo({top: 0, behavior: "instant"});
    }, [roundIndex]);

    useEffect(() => {
        const clientGuess = clientGuesses[roundIndex];
        const merged = mergeProgress(
            gameDate ? loadYearDay(gameDate)?.rounds[roundIndex] : undefined,
            serverGuess ?? clientGuess,
        );
        if (merged && merged.appId === appId) {
            setProgress(merged);
            if (merged.lastGuessYear && !guessYearInput) {
                setGuessYearInput(String(merged.lastGuessYear));
            }
        }
    }, [gameDate, roundIndex, serverGuess, clientGuesses, appId, guessYearInput]);

    useEffect(() => {
        if (!guessState.ok || !guessState.response || !gameDate) return;
        const response = guessState.response;
        const nextProgress: YearRoundProgress = {
            appId,
            pickName,
            hintsUsed: response.hintsUsed,
            revealedHints: progress?.revealedHints ?? [],
            unlockableHintLevels: response.unlockableHintLevels,
            lastDistance: response.distance,
            lastGuessYear: response.guessYear,
            completed: response.correct,
            actualYear: response.releaseYear ?? undefined,
            points: response.points ?? undefined,
        };
        setProgress(nextProgress);
        const saved = saveYearRound(gameDate, roundIndex, totalRounds, nextProgress);
        if (saved?.rounds[roundIndex]) setProgress(saved.rounds[roundIndex]);
    }, [guessState, gameDate, roundIndex, totalRounds, appId, pickName, progress?.revealedHints]);

    useEffect(() => {
        if (!hintState.ok || !hintState.response || !gameDate) return;
        const response = hintState.response;
        const existing = progress?.revealedHints ?? [];
        const revealed: RevealedHint[] = [
            ...existing.filter((hint) => hint.level !== response.hintLevel),
            {level: response.hintLevel, content: response.content},
        ];
        const nextProgress: YearRoundProgress = {
            appId,
            pickName,
            hintsUsed: response.hintsUsed,
            revealedHints: revealed,
            unlockableHintLevels: progress?.unlockableHintLevels ?? [],
            lastDistance: progress?.lastDistance,
            lastGuessYear: progress?.lastGuessYear,
            completed: progress?.completed ?? false,
            actualYear: progress?.actualYear,
            points: progress?.points,
        };
        setProgress(nextProgress);
        saveYearRound(gameDate, roundIndex, totalRounds, nextProgress);
        setProgress(nextProgress);
    }, [hintState, gameDate, roundIndex, totalRounds, appId, pickName, progress]);

    const {isSignedIn, isLoading: authLoading, refreshAuth} = useAuth();
    const signedIn = authLoading ? null : isSignedIn;
    const signedOutDuringPlay = computeSignedOutDuringPlay(signedIn, guessState);

    useEffect(() => {
        if (signedOutDuringPlay) refreshAuth();
    }, [signedOutDuringPlay, refreshAuth]);

    const completed = Boolean(progress?.completed);
    const unlockableLevels = progress?.unlockableHintLevels ?? [];
    const revealedHints = progress?.revealedHints ?? [];
    const maxPoints = hintTiers[0]?.maxPoints ?? 5;

    const cloneFormData = (formData: FormData) => {
        const copy = new FormData();
        formData.forEach((value, key) => copy.append(key, value));
        return copy;
    };

    const submitGuess = (formData: FormData) => {
        startGuessTransition(() => guessAction(formData));
    };

    const handleAuthGuardedSubmit = async (formData: FormData) => {
        const dismissed = hasDismissedAuthWarning();
        const fetched = (dismissed || signedIn === false) ? false : await fetchSignedIn();
        const live = resolveLiveSignedIn(signedIn, fetched);
        if (shouldWarnBeforeSubmit(dismissed, live)) {
            if (signedIn !== false) refreshAuth();
            setPendingFormData(cloneFormData(formData));
            setShowAuthWarning(true);
            return;
        }
        submitGuess(formData);
    };

    const revealHint = (hintLevel: number) => {
        const formData = new FormData();
        formData.set('appId', String(appId));
        formData.set('hintLevel', String(hintLevel));
        startHintTransition(() => hintAction(formData));
    };

    const handleLogin = () => {
        setShowAuthWarning(false);
        setPendingFormData(null);
        window.location.href = buildSteamLoginUrl();
    };

    const handleSkip = (reason?: "backdrop" | "button" | "escape") => {
        setShowAuthWarning(false);
        if (reason === "backdrop") return;
        if (pendingFormData) {
            const data = pendingFormData;
            setPendingFormData(null);
            submitGuess(data);
        }
    };

    const handleIgnore = () => {
        dismissAuthWarning();
        setShowAuthWarning(false);
        if (pendingFormData) {
            const data = pendingFormData;
            setPendingFormData(null);
            submitGuess(data);
        }
    };

    const restoreHintsFromServer = async (hintsUsed: number) => {
        if (!signedIn || hintsUsed <= 0 || hintsRestoredRef.current) return;
        hintsRestoredRef.current = true;
        for (let level = 1; level <= hintsUsed; level++) {
            if (revealedHints.some((hint) => hint.level === level)) continue;
            const formData = new FormData();
            formData.set('appId', String(appId));
            formData.set('hintLevel', String(level));
            await revealYearHintAction(undefined, formData).then((result) => {
                if (result.ok && result.response && gameDate) {
                    setProgress((current) => {
                        if (!current) return current;
                        const revealed = [
                            ...current.revealedHints.filter((hint) => hint.level !== result.response!.hintLevel),
                            {level: result.response!.hintLevel, content: result.response!.content},
                        ];
                        const next: YearRoundProgress = {
                            ...current,
                            hintsUsed: result.response!.hintsUsed,
                            revealedHints: revealed,
                        };
                        saveYearRound(gameDate, roundIndex, totalRounds, next);
                        return next;
                    });
                }
            });
        }
    };

    useEffect(() => {
        if (!progress || serverGuessesLoading || !signedIn) return;
        if ((progress.hintsUsed ?? 0) > revealedHints.length) {
            void restoreHintsFromServer(progress.hintsUsed);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [progress?.hintsUsed, serverGuessesLoading, signedIn]);

    return (
        <>
            {!completed && (
                <section className="year-guesser-round__guess-card" aria-labelledby="year-guess-submission">
                    <div className="year-guesser-round__guess-header">
                        <h2 id="year-guess-submission">Guess the release year</h2>
                        <OtherPlayersNow/>
                    </div>
                    <p className="text-muted">No buckets — enter any year. Far-off guesses unlock hints (fewer points each).</p>
                    <form
                        className="year-guesser-round__guess-form"
                        onSubmit={(event) => {
                            event.preventDefault();
                            const formData = new FormData(event.currentTarget);
                            void handleAuthGuardedSubmit(formData);
                        }}
                    >
                        <label>
                            <span className="sr-only">Release year</span>
                            <input
                                className="year-guesser-round__year-input"
                                type="number"
                                name="guessYear"
                                min={1970}
                                max={2100}
                                step={1}
                                required
                                inputMode="numeric"
                                value={guessYearInput}
                                onChange={(event) => setGuessYearInput(event.target.value)}
                                disabled={isGuessPending}
                                placeholder="e.g. 2018"
                            />
                        </label>
                        <input type="hidden" name="appId" value={appId}/>
                        <button type="submit" className="btn-cta" disabled={isGuessPending || !guessYearInput}>
                            {isGuessPending ? 'Checking…' : 'Submit guess'}
                        </button>
                    </form>

                    {progress?.lastDistance != null && !completed && (
                        <p className="year-guesser-round__feedback">
                            {progress.lastGuessYear} is <strong>{progress.lastDistance}</strong> year{progress.lastDistance === 1 ? '' : 's'} off.
                            {unlockableLevels.length > 0 && ' A hint is available below.'}
                        </p>
                    )}

                    {(unlockableLevels.length > 0 || revealedHints.length > 0) && (
                        <div className="year-guesser-round__hints">
                            <h3>Hints</h3>
                            <ul className="year-guesser-round__hint-list">
                                {hintTiers.filter((tier) => tier.level > 0).map((tier) => {
                                    const revealed = revealedHints.find((hint) => hint.level === tier.level);
                                    const unlockable = unlockableLevels.includes(tier.level);
                                    const isNext = tier.level === (progress?.hintsUsed ?? 0) + 1;
                                    return (
                                        <li
                                            key={tier.level}
                                            className={`year-guesser-round__hint-item${revealed ? ' year-guesser-round__hint-item--revealed' : ''}`}
                                        >
                                            <div className="year-guesser-round__hint-label">{tier.label}</div>
                                            <p className="year-guesser-round__hint-meta">{tier.description} · max {tier.maxPoints} pts</p>
                                            {revealed ? (
                                                <p>{revealed.content}</p>
                                            ) : unlockable && isNext ? (
                                                signedIn ? (
                                                    <button
                                                        type="button"
                                                        className="btn-ghost"
                                                        disabled={isHintPending}
                                                        onClick={() => revealHint(tier.level)}
                                                    >
                                                        Reveal hint
                                                    </button>
                                                ) : (
                                                    <p className="text-muted">
                                                        <button type="button" className="btn-link" onClick={() => {
                                                            window.location.href = buildSteamLoginUrl();
                                                        }}>Sign in with Steam</button> to reveal hints.
                                                    </p>
                                                )
                                            ) : (
                                                <p className="text-muted">Locked — guess further off to unlock.</p>
                                            )}
                                        </li>
                                    );
                                })}
                            </ul>
                        </div>
                    )}

                    {signedOutDuringPlay ? (
                        <p className="text-muted year-guesser-round__error">
                            You&apos;ve been signed out, so this result wasn&apos;t saved.{" "}
                            <button type="button" className="btn-link" onClick={() => {
                                window.location.href = buildSteamLoginUrl();
                            }}>Sign in with Steam</button> to save progress.
                        </p>
                    ) : guessState && !guessState.ok && guessState.error && (
                        <p className="text-muted year-guesser-round__error">Error: {guessState.error}</p>
                    )}
                    {hintState && !hintState.ok && hintState.error && hintState.error !== 'unauthorized' && (
                        <p className="text-muted year-guesser-round__error">Hint error: {hintState.error}</p>
                    )}
                </section>
            )}

            {completed && progress && (
                <section className="year-guesser-round__result year-guesser-round__result--success" aria-live="polite">
                    <h2>Correct — {progress.actualYear}</h2>
                    <p>
                        You guessed <strong>{progress.lastGuessYear}</strong>.
                        {progress.hintsUsed > 0
                            ? ` Used ${progress.hintsUsed} hint${progress.hintsUsed === 1 ? '' : 's'}.`
                            : ' No hints used.'}
                    </p>
                    <p><strong>{progress.points ?? maxPoints}</strong> points (max was {maxPoints - progress.hintsUsed}).</p>
                    <RoundResultActions
                        appId={appId}
                        prevHref={prevHref}
                        nextHref={roundIndex < totalRounds ? nextHref : null}
                    >
                        {signedIn === false && (
                            <p className="text-muted year-guesser-round__signin-nudge">
                                <button type="button" className="btn-link" onClick={() => {
                                    window.location.href = buildSteamLoginUrl();
                                }}>Sign in with Steam</button> to save results and appear on leaderboards when they launch.
                            </p>
                        )}
                    </RoundResultActions>
                </section>
            )}

            <AuthWarningModal
                isOpen={showAuthWarning}
                onLogin={handleLogin}
                onSkip={handleSkip}
                onIgnore={handleIgnore}
            />
        </>
    );
}
