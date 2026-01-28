---
name: ReviewGuesser fetch fix
overview: Adjust client/server guess merging and add loading state handling to fix partial SSR data and improve transitional UI behavior.
todos:
  - id: update-hook-loading
    content: Add loading state to useServerGuesses and return it
    status: completed
  - id: merge-results
    content: Merge SSR and client results with SSR precedence
    status: completed
  - id: share-loading-logic
    content: Adjust share visibility for loading transition
    status: completed
isProject: false
---

## Goals

- Ensure client fetch is only disabled when SSR results are complete
- Merge SSR and client guesses with SSR precedence
- Track client fetch loading state and use it to control share visibility and loading transitions

## Key files

- [frontend/src/components/ReviewGuesserRound.tsx](frontend/src/components/ReviewGuesserRound.tsx)
- [frontend/src/lib/hooks/useServerGuesses.ts](frontend/src/lib/hooks/useServerGuesses.ts)

## Plan

- Update `useServerGuesses` to return `{ guesses, loading }`, set `loading` true before fetch and false on success or failure, and keep the existing cancel guard.
- In `ReviewGuesserRound.tsx`, compute `totalRoundsFromState` as the existing `totalRounds` prop, then:
- Update `disableClientFetch` to check `Object.keys(allResults).length >= totalRounds` (prefilled still disables).
- Transform client guesses into `StoredRoundResult` and merge `{...clientResults, ...allResults}` so SSR wins per index.
- Use the new `loading` state in `canShowShare` and in `RoundShareSummary` rendering to avoid showing incomplete share UI during fetch.
- Ensure dependencies include `serverGuesses` so re-renders occur when guesses arrive.

## Notes

- Keep totalRounds as a prop from the page (`today.picks.length`) as agreed; no change to the page component.