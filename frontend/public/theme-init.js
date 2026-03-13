/**
 * Blocking script that initializes theme before React hydration.
 * This script runs synchronously before React loads to prevent FOUC.
 */
(function () {
    var KNOWN_THEMES = [
        'light',
        'dark',
        'oled',
        'hacker',
        'rainbow',
        'minimalist',
        'bold',
        'night',
        'bright'
    ];

    function isKnownTheme(value) {
        return KNOWN_THEMES.indexOf(value) !== -1;
    }

    try {
        var stored = null;
        try {
            stored = localStorage.getItem('theme');
        } catch (_) {
            stored = null;
        }

        var theme;
        if (stored && isKnownTheme(stored)) {
            theme = stored;
        } else {
            var prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
            theme = prefersDark ? 'dark' : 'light';
        }

        document.documentElement.setAttribute('data-theme', theme);
    } catch (e) {
        // Fallback to system preference if localStorage or dataset fails
        if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            document.documentElement.setAttribute('data-theme', 'dark');
        } else {
            document.documentElement.setAttribute('data-theme', 'light');
        }
    }
})();
