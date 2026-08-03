/**
 * Computes the next "which comment's reaction picker is open" id.
 *
 * Only one picker may be open across a comment list at a time. Opening a
 * picker always wins outright (covers switching focus from picker A to
 * picker B via keyboard activation, which never fires the outside-click
 * listener that would otherwise close A). A close signal only takes effect
 * when it comes from the picker that is currently open — a stale close
 * signal from a picker that already lost focus must not clobber a
 * different picker that has since opened.
 */
export function nextOpenPickerId(
    prev: number | null,
    commentId: number,
    isOpen: boolean,
): number | null {
    if (isOpen) return commentId;
    return prev === commentId ? null : prev;
}
