"use client";

import {useEffect, useRef} from "react";
import {useRouter} from "next/navigation";

/**
 * Wires global ArrowLeft / ArrowRight keys to previous / next round navigation.
 *
 * No-ops while {@code disabled} is set (e.g. an auth modal is open), when modifier
 * keys are held, when the event originates from a text field or editable element, or
 * while a Fancybox lightbox owns the arrow keys.
 */
/**
 * True while a modal dialog — the Fancybox lightbox or one of the app's own modals — is open.
 * `:modal` matches only dialogs opened with `showModal()`, so the inline, non-modal result card
 * does not suppress arrow-key navigation.
 */
export function hasOpenModalDialog(): boolean {
    return typeof document !== "undefined" && document.querySelector("dialog:modal") !== null;
}

export default function useRoundArrowNavigation(opts: {
    prevHref: string | null;
    nextHref: string | null;
    hasNextRound: boolean;
    disabled?: boolean;
}): void {
    const {prevHref, nextHref, hasNextRound, disabled = false} = opts;
    const router = useRouter();
    const handlerRef = useRef<(event: KeyboardEvent) => void>(() => {});

    useEffect(() => {
        handlerRef.current = (event: KeyboardEvent) => {
            const shouldIgnoreArrowNavigation = (): boolean => {
                if (event.defaultPrevented || event.repeat) return true;
                if (disabled) return true;
                if (event.metaKey || event.ctrlKey || event.altKey) return true;

                const target = event.target;
                if (target instanceof HTMLElement) {
                    if (target.isContentEditable) return true;
                    if (target.closest('input, textarea, select, [contenteditable="true"], [role="textbox"]')) {
                        return true;
                    }
                }

                // A modal owns the arrow keys: Fancybox v6 renders a native
                // <dialog class="fancybox__dialog"> opened with showModal(), and the app's own
                // auth/confirm modals do the same. The test must be `:modal`, not `[open]`: the
                // round result card is a deliberately non-modal `<dialog open>` that stays open
                // for the rest of the visit, so `[open]` suppressed the shortcut for the whole
                // normal play loop. (The earlier ".fancybox__container.is-open" test matched a
                // class the installed bundle never emits, which is why it was replaced.)
                if (hasOpenModalDialog()) return true;

                return false;
            };
            if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
            if (shouldIgnoreArrowNavigation()) return;

            if (event.key === "ArrowLeft") {
                if (!prevHref) return;
                event.preventDefault();
                router.push(prevHref);
                return;
            }

            if (!hasNextRound || !nextHref) return;
            event.preventDefault();
            router.push(nextHref);
        };
    }, [hasNextRound, nextHref, prevHref, router, disabled]);

    useEffect(() => {
        const handleKeyDown = (event: KeyboardEvent) => {
            handlerRef.current(event);
        };
        document.addEventListener("keydown", handleKeyDown);
        return () => {
            document.removeEventListener("keydown", handleKeyDown);
        };
    }, []);
}
