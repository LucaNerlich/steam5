/**
 * Whether focus is currently inside the given container.
 *
 * Popups that restore focus to their trigger from a document-level Escape
 * handler must only do so while focus is actually inside the popup's root —
 * without the containment check, Escape pressed anywhere else on the page
 * (e.g. the comment composer) yanks focus into the trigger.
 */
export function focusWithinElement(root: Element | null, activeElement: Element | null): boolean {
    if (!root || !activeElement) return false;
    return root.contains(activeElement);
}
