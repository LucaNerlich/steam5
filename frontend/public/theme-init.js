/**
 * Blocking script that initializes theme before React hydration.
 * This script runs synchronously before React loads to prevent FOUC.
 * The theme logic is duplicated here for the blocking script; ThemeSelector component
 * uses the centralized implementation from src/lib/theme/initTheme.ts.
 */
(function () {
    try {
        const stored = localStorage.getItem('theme');
        const theme = stored === 'light' || stored === 'dark'
            ? stored
            : (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
        if (theme === 'dark') {
            document.documentElement.setAttribute('data-theme', 'dark');
        } else {
            document.documentElement.removeAttribute('data-theme');
        }
    } catch (e) {
        // Fallback to system preference if localStorage fails
        if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            document.documentElement.setAttribute('data-theme', 'dark');
        } else {
            document.documentElement.removeAttribute('data-theme');
        }
    }
})();
