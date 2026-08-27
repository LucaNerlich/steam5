"use client";

import React, {useEffect, useEffectEvent, useRef} from "react";
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
 * Generic accessible confirm/cancel dialog built on the native <dialog>
 * element (showModal provides focus trap, Escape handling, and scroll
 * lock). The parent still owns open state via `isOpen`.
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
    const dialogRef = useRef<HTMLDialogElement | null>(null);
    const contentRef = useRef<HTMLDivElement | null>(null);

    // Clicks on ::backdrop target the <dialog> element itself; attached in the
    // effect so the non-interactive <dialog> keeps no JSX interaction handler.
    const onBackdropClick = useEffectEvent((event: MouseEvent) => {
        const content = contentRef.current;
        if (event.target === dialogRef.current || (content && !content.contains(event.target as Node))) {
            onCancel();
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
            className="confirm-modal__card"
            aria-labelledby="confirm-modal-title"
            onCancel={(event) => {
                // Escape: keep the parent as the single source of truth for open state.
                event.preventDefault();
                onCancel();
            }}
        >
            <div ref={contentRef}>
            <h2 id="confirm-modal-title">{title}</h2>
            {message && <p className="text-muted">{message}</p>}
            <div className="confirm-modal__actions">
                <button type="button" className="btn-cta" onClick={onConfirm}>
                    {confirmLabel}
                </button>
                {/* autoFocus: default focus to the last action (Cancel) rather
                    than the first (Confirm), so an accidental Enter can't
                    trigger a destructive action — per WAI-ARIA guidance for
                    confirmation dialogs. The native dialog focusing steps honor
                    `autofocus` over the first focusable element. */}
                <button type="button" className="btn-ghost" onClick={onCancel} autoFocus>
                    {cancelLabel}
                </button>
            </div>
            </div>
        </dialog>
    );
}
