# State machines

The app has several places where a UI or a lifecycle moves through phases. Most
of them are **implicit**: they are encoded as a handful of booleans and nullable
fields, and the legal combinations live in the reader's head (or in a comment).

This file is the inventory. It exists so a developer or an agent can answer
"what states can this be in, and what happens between them?" without rebuilding
the answer from six `useState` calls.

## Decision

1. **One union per machine.** A machine is a discriminated union named
   `<Thing>Play` / `<Thing>Status` / `<Thing>State`, never two independent
   booleans. If a combination is impossible, the type makes it unrepresentable.
2. **Pure transitions and derivations.** The phase rules live in a pure
   function under `frontend/src/lib/`, with a colocated `*.test.ts`. Components
   render the phase; they do not decide it.
3. **Derive, don't mirror.** If a value already exists in a store (`localStorage`,
   SWR, the server), derive the view from it. Do not copy it into `useState` and
   keep the copy in sync with an effect.
4. **Persisted lifecycles are enums in the database.** A lifecycle that outlives
   a request (seasons, archived comments) is a Flyway-backed enum/flag, and every
   transition is guarded (conditional `UPDATE`, date check) so two instances
   cannot both "win".
5. **No state-machine library.** The machines are small; a dependency would cost
   more than the unions.

## Inventory

### Frontend

| # | Machine | Where | States | Representation |
|---|---------|-------|--------|----------------|
| 1 | Round play | `src/lib/roundPlay.ts`, `ReviewGuesserRound.tsx` | `open` → `submitting` → `graded` \| `rejected` | **Explicit union** (`RoundPlay`) |
| 2 | Guess persistence | `src/lib/guessResolution.ts` (`RoundSubmission`, `resolveSubmissionTarget`) | captured → persisted | One-shot identity latch |
| 3 | Day progress | `src/lib/storage.ts`, `src/lib/rounds.ts` | empty → in progress → complete | Derived from `results` vs `totalRounds` |
| 4 | Auth session | `src/contexts/AuthContext.tsx`, `src/lib/authGuard.ts`, `useAuthWarningGate.ts` | signed out → signed in → signed out (lost mid-round) | Boolean + "lost session" transition detector |
| 5 | Presence socket | `src/lib/hooks/useRoundPresence.ts`, `OtherPlayersNow.tsx` | `idle` → `connecting` → `live` → `reconnecting` | **Explicit union** (`PresenceStatus`) |
| 6 | Comment composer | `DayComments.tsx` (`composerReducer`) | draft, mention menu, mention rate limit, transient "Posted" | Explicit reducer (`ComposerState`) |
| 7 | Comment submission | `app/review-guesser/comments/actions.ts` (`CommentActionState`) | idle → posting → posted \| failed \| outcome unknown | Action-state union |
| 8 | Comment moderation | `DayComments.tsx` | archiving one comment, error per comment, confirm modal | Independent UI overlays |
| 9 | Reaction toggle | `ReactionBar.tsx` | idle → pending(type) → done \| error | `pending` + `error` (mutually exclusive by construction) |
| 10 | Archive offline round | `ArchiveOfflineRound.tsx` (`storedSelection`) | unguessed → guessed | **Derived** from the localStorage store (no mirror) |
| 11 | Leaderboard feed | `LeaderboardTable.tsx` (SWR) | loading → fresh/stale → error | SWR's own lifecycle; stale data stays visible |
| 12 | Season (read side) | `src/types/seasons.ts` | `PLANNED` \| `ACTIVE` \| `FINALIZED` | Explicit string union matching the backend enum |

Rows 2–12 are left as they are on purpose: the representation already prevents
invalid states (explicit reducer, one-shot latch, enum), the platform owns the
lifecycle (SWR, the browser socket), or the value is a single derivation with no
transitions to guard.

### Backend

| # | Machine | Where | States | Representation |
|---|---------|-------|--------|----------------|
| 13 | Season lifecycle | `SeasonStatus`, `SeasonService.finalizeSeason`, `SeasonRepository.claimForFinalization` | `PLANNED` → `ACTIVE` → `FINALIZED` | DB enum + conditional `UPDATE` claim |
| 14 | Daily pick generation | `ReviewGameStateService.generateDailyPicks` | unlocked → generating → locked (failure stays unlocked) | `AtomicBoolean` + advisory-lock row |
| 15 | Presence sessions | `RoundPresenceService`, `RegisterResult` | registering → registered → unregistered | Enum result + counters |
| 16 | WebSocket ticket | `WsTicketService` | issued → consumed \| expired | One-shot Caffeine entry with TTL |
| 17 | Comment visibility | `Comment.archived` + `archivedAt` | visible → archived | Boolean + timestamp pair (written together) |
| 18 | Auth token validity | `AuthTokenService.verifyToken` | valid → expired \| invalidated | JWT expiry + per-user `tokenNotValidBefore` cutoff |

Deliberately **not** state machines: `IngestState` (monotonic cursor),
`LeaderboardRefreshState` (last-refresh checkpoint), rate limiters (fixed
window), table sort state, theme/font preferences, and one-shot feedback flags
(the "Copied" label in `ShareControls`).

## The two machines that are now explicit

### Round play (`RoundPlay`)

Inputs: the server action result, the stored/server result for the round, the
live selection, whether a submit/probe is in flight, and auth state.

```
open ──submit──▶ submitting ──ok──▶ graded
                    │
                    └──error──▶ rejected ──retry──▶ submitting
open ──restored result────────────────────────────▶ graded
```

Invariants encoded by `deriveRoundPlay`:

- A response graded for another `appId` never grades this round (the page
  component is reused across rounds).
- A failed submission stays `rejected`, so the bucket buttons remain enabled and
  the selection is kept for a one-click retry.
- `graded` wins over a pending retry (the buttons are not rendered then).
- `signedOut` is only read from an action state stamped for this pick.

Tests: `src/lib/roundPlay.test.ts`.

### Presence (`PresenceStatus`)

```
idle ──start──▶ connecting ──open──▶ live
                   │                  │
                   └──drop────────────┴──▶ reconnecting ──open──▶ live
                                              │
                                              └──hidden/paused──▶ idle
```

Before this change the socket exposed `connected` and `reconnecting` as two
booleans, so "connected and reconnecting" was representable, and a tab that was
hidden while reconnecting could stay stuck on "Reconnecting…". `pause()` and a
hidden-tab drop now transition to `idle`.

Tests: `src/lib/hooks/useRoundPresence.test.ts`.

## Adding or changing a machine

1. Write the union and the pure transition/derivation in `frontend/src/lib/`.
2. Add a test that walks every phase and the illegal-transition guards (stale
   response, wrong scope, missing identity).
3. Make the component render `switch (state.phase)` instead of combinations of
   booleans. Delete the booleans it replaced.
4. If the lifecycle is persisted, add the enum/flag and the guarded transition
   in a new Flyway migration; never edit an applied one.
