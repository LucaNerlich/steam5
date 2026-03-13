/**
 * Blocking script that initializes theme before React hydration.
 * This script runs synchronously before React loads to prevent FOUC.
 */
(function () {
    function parseKnownThemesFromScript() {
        var script = document.currentScript;
        if (!script || !script.getAttribute) return null;

        var raw = script.getAttribute('data-known-themes');
        if (!raw) return null;

        var parts = raw.split(',');
        var themes = [];
        for (var i = 0; i < parts.length; i += 1) {
            var candidate = parts[i].trim();
            if (candidate) {
                themes.push(candidate);
            }
        }

        return themes.length > 0 ? themes : null;
    }

    function isSafeThemeToken(value) {
        return /^[a-z0-9-]{1,32}$/.test(value);
    }

    function isKnownTheme(value, knownThemes) {
        if (!isSafeThemeToken(value)) return false;
        if (!knownThemes || knownThemes.length === 0) return true;
        return knownThemes.indexOf(value) !== -1;
    }

    try {
        var knownThemes = parseKnownThemesFromScript();
        var stored = null;
        var hasStorage = true;
        try {
            stored = localStorage.getItem('theme');
        } catch (_) {
            hasStorage = false;
            stored = null;
        }

        var theme;
        if (stored && isKnownTheme(stored, knownThemes)) {
            theme = stored;
        } else {
            if (stored && hasStorage) {
                try {
                    localStorage.removeItem('theme');
                } catch (_) {
                    // Ignore removal failures in restricted storage contexts.
                }
            }
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
