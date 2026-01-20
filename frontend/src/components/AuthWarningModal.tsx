"use client";

import React from "react";
import "@/styles/components/authWarningModal.css";

type AuthWarningModalProps = {
    isOpen: boolean;
    onLogin: () => void;
    onSkip: (reason?: "backdrop" | "button") => void;
};

export default function AuthWarningModal({
    isOpen,
    onLogin,
    onSkip
}: Readonly<AuthWarningModalProps>): React.ReactElement | null {
    if (!isOpen) return null;

    return (
        <div
            className="auth-warning-modal__backdrop"
            onClick={() => onSkip("backdrop")}
        >
            <div
                className="auth-warning-modal__card"
                role="dialog"
                aria-modal="true"
                aria-labelledby="auth-warning-title"
                onClick={(event) => event.stopPropagation()}
            >
                <h2 id="auth-warning-title">Log in to join the leaderboard</h2>
                <p className="text-muted">
                    You can keep guessing, but your round results will not count
                    toward the leaderboard unless you sign in.
                </p>
                <div className="auth-warning-modal__actions">
                    <button className="btn-cta" onClick={onLogin}>
                        Log In
                    </button>
                    <button className="btn-ghost" onClick={() => onSkip("button")}>
                        Continue Anyway
                    </button>
                </div>
            </div>
        </div>
    );
}
