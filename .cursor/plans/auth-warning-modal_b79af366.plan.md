---
name: auth-warning-modal
overview: Add an auth warning modal on round 1 submissions, intercept unauthenticated guesses, and update form handling to support the interceptor pattern.
todos:
  - id: auth-warning-component
    content: Add AuthWarningModal component and styles
    status: completed
  - id: intercept-submit
    content: Integrate warning/interceptor in ReviewGuesserRound
    status: completed
  - id: guessbuttons-submit
    content: Update GuessButtons to onSubmit interceptor
    status: completed
---

# Auth Warning Modal Plan

## Decisions

- Treat `signedIn === null` as unauthenticated for the warning.
- Backdrop click closes the modal without submitting.

## Files to change

- Add [`frontend/src/components/AuthWarningModal.tsx`](frontend/src/components/AuthWarningModal.tsx) client component with modal markup, callbacks, and stylesheet import.
- Add [`frontend/src/styles/components/authWarningModal.css`](frontend/src/styles/components/authWarningModal.css) using `reviewRoundResult.css` as a visual reference.
- Update [`frontend/src/components/ReviewGuesserRound.tsx`](frontend/src/components/ReviewGuesserRound.tsx) to intercept round 1 submissions, store pending `FormData`, and render the modal.
- Update [`frontend/src/components/GuessButtons.tsx`](frontend/src/components/GuessButtons.tsx) to submit via `onSubmit` and call the interceptor with a constructed `FormData`.

## Implementation outline

- **AuthWarningModal component**
- Implement a `use client` component with props `isOpen`, `onLogin`, `onSkip`.
- Render semantic dialog container with `role="dialog"` and `aria-modal="true"` plus a message about leaderboard participation requiring login.
- Buttons: “Log In” (`.btn-cta`) and “Continue Anyway” (`.btn-ghost`).
- Backdrop overlay closes on click; prevent propagation for clicks inside the card.
- Import [`frontend/src/styles/components/authWarningModal.css`](frontend/src/styles/components/authWarningModal.css).

- **Styling**
- Create `.auth-warning-modal__backdrop` fixed overlay with semi-transparent background and high `z-index`.
- Center `.auth-warning-modal__card` using flexbox; match padding, border, radius, and shadow to [`frontend/src/styles/components/reviewRoundResult.css`](frontend/src/styles/components/reviewRoundResult.css).

- **ReviewGuesserRound integration**
- Add state: `showAuthWarning` and `pendingFormData` (`FormData | null`).
- Wrap `formAction` in an interceptor:
- If `roundIndex === 1` and `signedIn !== true`, set pending data and show modal.
- Otherwise call `formAction(formData)`.
- `handleLogin`: `window.location.href = buildSteamLoginUrl()`.
- `handleSkip`: hide modal and submit stored `pendingFormData` via `formAction` if present.
- Render `<AuthWarningModal>` when `showAuthWarning` is true.
- Pass interceptor into `GuessButtons` instead of raw `formAction`.

- **GuessButtons update**
- Replace `<Form action={formAction}>` with a standard `<form onSubmit={...}>`.
- In `onSubmit`, `preventDefault()`, create `FormData` from the form element, and call the passed `formAction(formData)`.
- Keep prop type as `(formData: FormData) => void` to remain compatible.

## Notes

- The interceptor must not submit when modal is open (to avoid double submissions).
- Keep client-only usage guarded by `use client` and avoid server-side references.