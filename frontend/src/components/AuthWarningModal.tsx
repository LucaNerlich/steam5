"use client";

import React, {useEffect, useEffectEvent, useRef} from "react";
import "@/styles/components/authWarningModal.css";

type AuthWarningModalProps = {
    isOpen: boolean;
    onLogin: () => void;
    onSkip: (reason?: "backdrop" | "button" | "escape") => void;
    onIgnore: () => void;
};

/**
 * Native <dialog> modal (showModal provides focus trap, Escape handling,
 * and scroll lock). The parent still owns open state via `isOpen`.
 */
export default function AuthWarningModal({
    isOpen,
    onLogin,
    onSkip,
    onIgnore
}: Readonly<AuthWarningModalProps>): React.ReactElement | null {
    const dialogRef = useRef<HTMLDialogElement | null>(null);
    const contentRef = useRef<HTMLDivElement | null>(null);

    // Clicks on ::backdrop target the <dialog> element itself; attached in the
    // effect so the non-interactive <dialog> keeps no JSX interaction handler.
    const onBackdropClick = useEffectEvent((event: MouseEvent) => {
        const content = contentRef.current;
        if (event.target === dialogRef.current || (content && !content.contains(event.target as Node))) {
            onSkip("backdrop");
        }
    });

    useEffect(() => {
        if (!isOpen) return;

        const dialog = dialogRef.current;
        if (!dialog) return;

        dialog.showModal();
        dialog.addEventListener("click", onBackdropClick);

        return () => {
            dialog.removeEventListener("click", onBackdropClick);
            dialog.close();
        };
    }, [isOpen]);

    if (!isOpen) return null;

    return (
        <dialog
            ref={dialogRef}
            className="auth-warning-modal__card"
            aria-labelledby="auth-warning-title"
            onCancel={(event) => {
                // Escape: keep the parent as the single source of truth for open state.
                event.preventDefault();
                onSkip("escape");
            }}
        >
            <div ref={contentRef}>
            <h2 id="auth-warning-title">Log in to join the leaderboard</h2>
            <p className="text-muted">
                You can keep guessing, but your round results will not count
                toward the leaderboard unless you sign in.
            </p>
            <div className="auth-warning-modal__actions">
                <button type="button" className="btn-cta" onClick={onLogin}>
                    Log In
                </button>
                <button type="button" className="btn-ghost" onClick={() => onSkip("button")}>
                    Continue Anyway
                </button>
            </div>
            <button
                type="button"
                className="auth-warning-modal__ignore"
                onClick={onIgnore}
            >
                <svg
                    className="auth-warning-modal__ignore-icon"
                    width="16"
                    height="16"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                >
                    <path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z"/>
                    <line x1="12" y1="9" x2="12" y2="13"/>
                    <line x1="12" y1="17" x2="12.01" y2="17"/>
                </svg>
                Ignore this warning
            </button>
            </div>
        </dialog>
    );
}
