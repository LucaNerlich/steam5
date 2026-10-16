/**
 * Decides whether a click on a native <dialog> counts as a "backdrop click".
 *
 * Clicks on the ::backdrop pseudo-element land on the <dialog> element itself,
 * and so does any part of the dialog box outside the tracked content element
 * (e.g. a padding ring left on the dialog instead of the content). Anything
 * inside the tracked content element — including the content's own padding —
 * belongs to the card and must never dismiss the modal.
 */
export function isBackdropClick(
    target: EventTarget | null,
    dialog: EventTarget | null,
    content: Element | null,
): boolean {
    // The <dialog> element itself: ::backdrop clicks are retargeted to it,
    // and so are clicks into padding the dialog owns itself.
    if (target === dialog) return true;
    if (!content) return false;
    // Inside the tracked content box: content.contains(h2) and also
    // content.contains(content) hold, so hits on the content's own
    // padding never count as backdrop clicks.
    return target !== null && !content.contains(target as Node);
}
