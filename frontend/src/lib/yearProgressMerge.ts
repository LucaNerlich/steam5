import type {MyYearGuess} from "@/types/year-game";
import {effectiveYearHintsUsed, yearPointsForHintsUsed} from "@/lib/yearScoring";
import type {RevealedHint, YearRoundProgress} from "@/lib/yearStorage";

export function mergeRevealedHints(...sources: (RevealedHint[] | undefined)[]): RevealedHint[] {
    const byLevel = new Map<number, RevealedHint>();
    for (const list of sources) {
        for (const hint of list ?? []) {
            byLevel.set(hint.level, hint);
        }
    }
    return [...byLevel.values()].sort((a, b) => a.level - b.level);
}

export function mergeYearProgress(
    local?: YearRoundProgress,
    server?: MyYearGuess,
    maxPointsAtStart = 5,
    current?: YearRoundProgress,
): YearRoundProgress | undefined {
    if (!local && !server && !current) return undefined;

    const revealedHints = mergeRevealedHints(local?.revealedHints, current?.revealedHints);
    const localHints = effectiveYearHintsUsed(local?.hintsUsed ?? 0, local?.revealedHints?.length ?? 0);
    const currentHints = effectiveYearHintsUsed(current?.hintsUsed ?? 0, current?.revealedHints?.length ?? 0);
    const revealedHintsCount = revealedHints.length;
    const serverHints = server?.hintsUsed ?? 0;
    const mergedHints = Math.max(localHints, currentHints, serverHints, revealedHintsCount);

    if (local?.completed || current?.completed) {
        const base = local?.completed ? local : current!;
        return {
            ...base,
            hintsUsed: mergedHints,
            revealedHints,
            actualYear: base.actualYear ?? server?.actualYear ?? undefined,
            points: yearPointsForHintsUsed(maxPointsAtStart, mergedHints),
            lastGuessYear: base.lastGuessYear ?? server?.guessedYear ?? undefined,
            lastGuessTooEarly: base.lastGuessTooEarly ?? current?.lastGuessTooEarly,
        };
    }

    const completed = Boolean(server?.completed || local?.completed || current?.completed);

    return {
        appId: server?.appId ?? local?.appId ?? current?.appId ?? 0,
        pickName: local?.pickName ?? current?.pickName,
        hintsUsed: mergedHints,
        revealedHints,
        unlockableHintLevels: completed
            ? []
            : (server?.unlockableHintLevels ?? local?.unlockableHintLevels ?? current?.unlockableHintLevels ?? []),
        lastDistance: local?.lastDistance ?? current?.lastDistance ?? server?.bestDistance ?? undefined,
        lastGuessYear: server?.guessedYear ?? local?.lastGuessYear ?? current?.lastGuessYear,
        lastGuessTooEarly: local?.lastGuessTooEarly ?? current?.lastGuessTooEarly,
        completed,
        actualYear: completed
            ? (server?.actualYear ?? local?.actualYear ?? current?.actualYear)
            : (local?.actualYear ?? current?.actualYear),
        points: completed
            ? yearPointsForHintsUsed(maxPointsAtStart, mergedHints)
            : (local?.points ?? current?.points),
    };
}
