"use client";

import React, {useEffect, useRef} from "react";
import "@/styles/components/confirmModal.css";

type ConfirmModalProps = {
    isOpen: boolean;
    title: string;
    message?: string;
    confirmLabel?: string;
    cancelLabel?: string;
    onConfirm: () => void;
    onCancel: () => void;
};

/**
 * Generic accessible confirm/cancel dialog, following the same backdrop,
 * focus-trap, and Escape-to-close behavior as AuthWarningModal.
 */
export default function ConfirmModal({
    isOpen,
    title,
    message,
    confirmLabel = "Confirm",
    cancelLabel = "Cancel",
    onConfirm,
    onCancel,
}: Readonly<ConfirmModalProps>): React.ReactElement | null {
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
            const fallback = modal.querySelector<HTMLElement>("#confirm-modal-title");
            const target = focusables[0] ?? fallback ?? modal;
            target?.focus();
        };

        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.key === "Escape") {
                event.preventDefault();
                onCancel();
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
    }, [isOpen, onCancel]);

    if (!isOpen) return null;

    return (
        <div
            className="confirm-modal__backdrop"
            role="presentation"
            onClick={onCancel}
            onKeyDown={(event) => { if (event.key === "Escape") onCancel(); }}
        >
            <div
                className="confirm-modal__card"
                role="dialog"
                aria-modal="true"
                aria-labelledby="confirm-modal-title"
                ref={modalRef}
                tabIndex={-1}
                onClick={(event) => event.stopPropagation()}
                onKeyDown={(event) => event.stopPropagation()}
            >
                <h2 id="confirm-modal-title" tabIndex={-1}>{title}</h2>
                {message && <p className="text-muted">{message}</p>}
                <div className="confirm-modal__actions">
                    <button type="button" className="btn-cta" onClick={onConfirm}>
                        {confirmLabel}
                    </button>
                    <button type="button" className="btn-ghost" onClick={onCancel}>
                        {cancelLabel}
                    </button>
                </div>
            </div>
        </div>
    );
}
