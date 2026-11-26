"use client";

import React, {useEffect, useEffectEvent, useRef} from "react";
import {isBackdropClick} from "@/lib/backdropClick";
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
    const cancelRef = useRef<HTMLButtonElement | null>(null);

    // Clicks on ::backdrop target the <dialog> element itself; attached in the
    // effect so the non-interactive <dialog> keeps no JSX interaction handler.
    const onBackdropClick = useEffectEvent((event: MouseEvent) => {
        if (isBackdropClick(event.target, dialogRef.current, contentRef.current)) {
            onCancel();
        }
    });

    useEffect(() => {
        if (!isOpen) return;

        const dialog = dialogRef.current;
        if (!dialog) return;

        dialog.showModal();
        // showModal() focuses the first focusable element — the destructive Confirm button,
        // because React's autoFocus prop only calls .focus() (a no-op while the dialog is
        // display:none) and never writes the autofocus attribute. Focus Cancel explicitly.
        cancelRef.current?.focus();
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
            <div ref={contentRef} className="confirm-modal__body">
            <h2 id="confirm-modal-title">{title}</h2>
            {message && <p className="text-muted">{message}</p>}
            <div className="confirm-modal__actions">
                <button type="button" className="btn-cta" onClick={onConfirm}>
                    {confirmLabel}
                </button>
                {/* Focused explicitly in the showModal effect: an accidental Enter must
                    cancel, not confirm (WAI-ARIA guidance for confirmation dialogs). */}
                <button ref={cancelRef} type="button" className="btn-ghost" onClick={onCancel}>
                    {cancelLabel}
                </button>
            </div>
            </div>
        </dialog>
    );
}
