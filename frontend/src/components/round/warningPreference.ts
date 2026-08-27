// Persisted UI preference (not a credential): when set, the "not signed in"
// warning is suppressed on every round so the user is not nagged on each
// submit. Kept in localStorage because it is a display preference, not a
// session token — it holds no secret and needs no HttpOnly protection.

const WARNING_DISMISSED_STORAGE_KEY = "s5_warning_dismissed";

export function hasDismissedAuthWarning(): boolean {
    if (typeof window === "undefined") return false;
    try {
        return window.localStorage.getItem(WARNING_DISMISSED_STORAGE_KEY) === "1";
    } catch {
        return false;
    }
}

export function dismissAuthWarning(): void {
    if (typeof window === "undefined") return;
    try {
        window.localStorage.setItem(WARNING_DISMISSED_STORAGE_KEY, "1");
    } catch {
        // Storage can be unavailable (e.g. private mode); the warning then
        // simply shows again on the next submit.
    }
}
