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

                // Fancybox owns the arrow keys while a lightbox is open. Fancybox v6 renders a
                // native <dialog class="fancybox__dialog"> and opens it with showModal(), so an
                // open dialog is the signal. The previously tested ".fancybox__container.is-open"
                // class is never emitted by the installed version (the bundle only ever adds
                // is-idle/is-ready/is-hiding/is-closing), which made this guard dead: with focus
                // inside the player, arrows routed away behind the open lightbox.
                if (document.querySelector("dialog[open]")) return true;

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
