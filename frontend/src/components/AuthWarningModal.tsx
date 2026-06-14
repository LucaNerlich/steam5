"use client";

import React, { useEffect, useRef } from "react";
import "@/styles/components/authWarningModal.css";

type AuthWarningModalProps = {
    isOpen: boolean;
    onLogin: () => void;
    onSkip: (reason?: "backdrop" | "button" | "escape") => void;
    onIgnore: () => void;
};

export default function AuthWarningModal({
    isOpen,
    onLogin,
    onSkip,
    onIgnore
}: Readonly<AuthWarningModalProps>): React.ReactElement | null {
    const modalRef = useRef<HTMLDivElement | null>(null);

    useEffect(() => {
        if (!isOpen) return;

        const modal = modalRef.current;
        if (!modal) return;

        const getFocusableElements = () =>
            Array.from(
                modal.querySelectorAll<HTMLElement>(
                    'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
                )
            ).filter((element) => !element.hasAttribute("disabled"));

        const focusFirstElement = () => {
            const focusables = getFocusableElements();
            const fallback = modal.querySelector<HTMLElement>("#auth-warning-title");
            const target = focusables[0] ?? fallback ?? modal;
            target?.focus();
        };

        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                event.preventDefault();
                onSkip("escape");
                return;
            }

            if (event.key !== "Tab") return;

            const focusables = getFocusableElements();
            if (focusables.length === 0) {
                event.preventDefault();
                modal.focus();
                return;
            }

            const first = focusables[0];
            const last = focusables[focusables.length - 1];
            const active = document.activeElement as HTMLElement | null;

            if (event.shiftKey) {
                if (!active || !modal.contains(active) || active === first) {
                    event.preventDefault();
                    last.focus();
                }
                return;
            }

            if (!active || !modal.contains(active) || active === last) {
                event.preventDefault();
                first.focus();
            }
        };

        document.addEventListener("keydown", handleKeyDown);
        const raf = requestAnimationFrame(() => {
            if (!modal.contains(document.activeElement)) {
                focusFirstElement();
            }
        });

        return () => {
            document.removeEventListener("keydown", handleKeyDown);
            cancelAnimationFrame(raf);
        };
    }, [isOpen, onSkip]);

    if (!isOpen) return null;

    return (
        <div
            className="auth-warning-modal__backdrop"
            role="presentation"
            onClick={() => onSkip("backdrop")}
            onKeyDown={(e) => { if (e.key === "Escape") onSkip("escape"); }}
        >
            <div
                className="auth-warning-modal__card"
                role="dialog"
                aria-modal="true"
                aria-labelledby="auth-warning-title"
                ref={modalRef}
                tabIndex={-1}
                onClick={(event) => event.stopPropagation()}
                onKeyDown={(event) => event.stopPropagation()}
            >
                <h2 id="auth-warning-title" tabIndex={-1}>
                    Log in to join the leaderboard
                </h2>
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
        </div>
    );
}
