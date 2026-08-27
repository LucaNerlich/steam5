// Persisted UI preference (not a credential): when set, the "not signed in"
// warning is suppressed on every round so the user is not nagged on each
// submit. Kept in localStorage because it is a display preference, not a
// session token — it holds no secret and needs no HttpOnly protection.

const WARNING_DISMISSED_STORAGE_KEY = "s5_warning_dismissed";

// Module-level cache to avoid repeated localStorage reads
let dismissedCache: boolean | null = null;

function invalidateCache(): void {
    dismissedCache = null;
}

// Invalidate cache when storage changes or tab becomes visible
if (typeof window !== "undefined") {
    window.addEventListener("storage", (e) => {
        if (e.key === WARNING_DISMISSED_STORAGE_KEY || e.key === null) {
            invalidateCache();
        }
    });
    document.addEventListener("visibilitychange", () => {
        if (document.visibilityState === "visible") {
            invalidateCache();
        }
    });
}

export function hasDismissedAuthWarning(): boolean {
    if (typeof window === "undefined") return false;
    if (dismissedCache !== null) return dismissedCache;
    try {
        dismissedCache = window.localStorage.getItem(WARNING_DISMISSED_STORAGE_KEY) === "1";
        return dismissedCache;
    } catch {
        return false;
    }
}

export function dismissAuthWarning(): void {
    if (typeof window === "undefined") return;
    try {
        window.localStorage.setItem(WARNING_DISMISSED_STORAGE_KEY, "1");
        dismissedCache = true;
    } catch {
        // Storage can be unavailable (e.g. private mode); the warning then
        // simply shows again on the next submit.
    }
}
