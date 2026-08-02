# Design: Gate DayComments by day-completion for logged-in users

## Problem

`DayComments.tsx` (rendered on the live round page via `ReviewGuesserRound.tsx:441`) currently shows the comment list and composer to every visitor regardless of progress. We want comments for a game date to only be visible to a logged-in player once they've completed all rounds for that day. Anonymous visitors should keep seeing comments unconditionally, with the composer already gated behind a "sign in" prompt (no change needed there).

## Behavior

| User state | Rounds complete for this day | Result |
|---|---|---|
| Anonymous | any | List visible, composer shows existing "Sign in with Steam to comment" link (unchanged) |
| Logged in | incomplete | Entire `DayComments` section hidden (no header, no count, no list, no composer) |
| Logged in | complete | List + composer visible and usable (unchanged) |

Readonly usage (`archive/[date]/page.tsx:85`) is unaffected — archive days are inherently past/complete and the gate does not apply there.

"Complete" means all rounds for the specific `gameDate` being viewed — the same completion check already computed independently in `RoundSummary.tsx` and `ShareControls.tsx`: merge localStorage day data (`review-guesser:{gameDate}`) with any server-provided `results` and the current `latestRound`/`latest` result, then compare the merged round-index count to `totalRounds`.

## Implementation

1. **`DayComments.tsx`**: add optional props matching `RoundSummary`/`ShareControls`'s existing signature — `totalRounds?: number`, `latestRound?: number`, `latest?: RoundResult`, `results?: Record<number, RoundResult>`. Compute `isComplete` inline using the same merge logic as those two components (no new shared helper — follows the existing repeated-computation convention in this codebase).
2. Add a gate immediately after the existing `if (!gameDate) return null;` line:
   ```
   if (!readOnly && isSignedIn === true && !isComplete) return null;
   ```
   When `readOnly` is true or `isSignedIn` is not `true` (anonymous or still loading), the gate is skipped and current behavior is preserved.
3. **`ReviewGuesserRound.tsx:441`**: pass the four new props through, reusing values already computed a few lines above for `RoundSummary`/`ShareControls` (`totalRounds`, `latestStoredRoundIndex` as `latestRound`, `latestResult` as `latest`, and the same `results` expression: `!serverGuessesLoading && hasServerResults ? serverResults : undefined`).
4. **`archive/[date]/page.tsx:85`**: no change — it doesn't pass the new props, `readOnly` is `true`, so the gate never triggers there.

## Non-goals

- No change to the anonymous composer UI (confirmed with user: keep the current compact "Sign in with Steam" link, not a visibly-disabled textarea).
- No backend change — purely a client-side visibility gate, same pattern as `ShareControls`'s existing `if (!isComplete) return null;`.
- No bypass for the comment moderator — moderators are subject to the same gate as any other logged-in user.

## Testing

- Manual verification in-browser (dev server): anonymous view shows comments; logged-in + incomplete day hides the whole section; logged-in + complete day shows list + composer as before; archive page unaffected.
