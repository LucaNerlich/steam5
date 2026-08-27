"use client";

import {useRef, useState} from "react";
import {buildSteamLoginUrl} from "@/lib/steamLogin";
import {resolveLiveSignedIn, shouldWarnBeforeSubmit} from "@/lib/authGuard";
import {dismissAuthWarning, hasDismissedAuthWarning} from "./warningPreference";

// Freshly verify the session at submit time. The cached signedIn flag can be
// stale (the s5_token cookie may have been dropped mid-session), and because that
// cookie is HttpOnly the client cannot inspect it directly — only the server can
// tell us. Errors are treated as "still signed in" so a transient failure never
// blocks a guess; the post-submit persisted check is the backstop.
async function fetchSignedIn(): Promise<boolean> {
    try {
        const r = await fetch('/api/auth/me', {cache: 'no-store'});
        if (r.status === 401) return false;
        if (!r.ok) return true;
        const data = await r.json();
        return Boolean(data?.signedIn);
    } catch {
        return true;
    }
}

function cloneFormData(formData: FormData): FormData {
    const copy = new FormData();
    formData.forEach((value, key) => {
        copy.append(key, value);
    });
    return copy;
}

type AuthWarningGate = {
    /** Whether the "not signed in" warning modal is currently open. */
    showAuthWarning: boolean;
    /** Wrap this as the guess form's action to run the auth guard first. */
    handleAuthGuardedSubmit: (formData: FormData) => Promise<void>;
    handleLogin: () => void;
    handleSkip: (reason?: "backdrop" | "button" | "escape") => void;
    handleIgnore: () => void;
};

/**
 * Owns the "not signed in" warning gate around guess submission: the modal
 * visibility, the deferred form payload held while the modal is open, and the
 * login/skip/ignore handlers. The deferred payload is kept in a ref — it is
 * imperative scratch data read only by the modal's button handlers, never
 * rendered, so it must not trigger renders.
 *
 * @param signedIn - Cached auth state from AuthContext (null while loading).
 * @param refreshAuth - Re-syncs the AuthContext with the server.
 * @param submitGuess - Performs the actual guess submission.
 */
export default function useAuthWarningGate(
    signedIn: boolean | null,
    refreshAuth: () => void,
    submitGuess: (formData: FormData) => void,
): AuthWarningGate {
    const [showAuthWarning, setShowAuthWarning] = useState(false);
    const pendingFormData = useRef<FormData | null>(null);

    const handleAuthGuardedSubmit = async (formData: FormData) => {
        const dismissed = hasDismissedAuthWarning();
        // Determine the live signed-in state. signedIn === false is reliable
        // (set from the server on load), so trust it directly; a dismissed user
        // never needs the check. Otherwise the cached value may be stale, so
        // re-validate before letting the guess count — this catches a session
        // lost mid-round (the HttpOnly s5_token cookie can vanish without the
        // client knowing).
        const fetched = (dismissed || signedIn === false) ? false : await fetchSignedIn();
        const live = resolveLiveSignedIn(signedIn, fetched);
        if (shouldWarnBeforeSubmit(dismissed, live)) {
            if (signedIn !== false) refreshAuth(); // sync header with the fresh value
            pendingFormData.current = cloneFormData(formData);
            setShowAuthWarning(true);
            return;
        }
        submitGuess(formData);
    };

    const handleLogin = () => {
        setShowAuthWarning(false);
        pendingFormData.current = null;
        window.location.href = buildSteamLoginUrl();
    };

    const handleSkip = (reason?: "backdrop" | "button" | "escape") => {
        setShowAuthWarning(false);
        if (reason === "backdrop") return;
        const data = pendingFormData.current;
        pendingFormData.current = null;
        if (data) submitGuess(data);
    };

    const handleIgnore = () => {
        dismissAuthWarning();
        setShowAuthWarning(false);
        const data = pendingFormData.current;
        pendingFormData.current = null;
        if (data) submitGuess(data);
    };

    return {showAuthWarning, handleAuthGuardedSubmit, handleLogin, handleSkip, handleIgnore};
}
