"use client";

import {useCallback, useEffect, useSyncExternalStore} from "react";

type Theme = "light" | "dark";

let currentTheme: Theme = "light";
const listeners = new Set<() => void>();

function notifyListeners() {
    for (const l of listeners) l();
}

function readThemeFromDOM(): Theme {
    if (typeof window === "undefined") return "light";
    const rootTheme = document.documentElement.getAttribute("data-theme");
    if (rootTheme === "dark") return "dark";
    const stored = window.localStorage.getItem("theme");
    if (stored === "light" || stored === "dark") return stored;
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? "dark" : "light";
}

function applyTheme(theme: Theme) {
    currentTheme = theme;
    const root = document.documentElement;
    if (theme === "dark") {
        root.setAttribute("data-theme", "dark");
    } else {
        root.removeAttribute("data-theme");
    }
    window.localStorage.setItem("theme", theme);
    notifyListeners();
}

function subscribe(callback: () => void) {
    listeners.add(callback);
    return () => { listeners.delete(callback); };
}

function getSnapshot(): Theme {
    return currentTheme;
}

function getServerSnapshot(): Theme {
    return "light";
}

if (typeof window !== "undefined") {
    currentTheme = readThemeFromDOM();
}

export default function ThemeToggle() {
    const theme = useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);

    useEffect(() => {
        const media = window.matchMedia('(prefers-color-scheme: dark)');
        const handler = () => {
            const stored = window.localStorage.getItem("theme");
            if (stored !== "light" && stored !== "dark") {
                applyTheme(media.matches ? "dark" : "light");
            }
        };
        media.addEventListener("change", handler);
        return () => media.removeEventListener("change", handler);
    }, []);

    const toggle = useCallback(() => {
        applyTheme(currentTheme === "dark" ? "light" : "dark");
    }, []);

    return (
        <button
            title='Theme Toggle'
            className="theme-toggle"
            aria-label="Toggle color theme"
            onClick={toggle}
        >
            {theme === null ? null : theme === "dark" ? (
                // Sun icon for switching to light
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"
                     focusable="false">
                    <path fill="currentColor"
                          d="M6.76 4.84l-1.8-1.79-1.41 1.41 1.79 1.8 1.42-1.42zm10.48 0l1.79-1.79 1.41 1.41-1.79 1.8-1.41-1.42zM12 4V1h-0v3h0zm0 19v-3h0v3h0zm8-11h3v0h-3v0zM1 12H4v0H1v0zm15.24 7.16l1.8 1.79 1.41-1.41-1.79-1.8-1.42 1.42zM4.84 17.24l-1.79 1.8 1.41 1.41 1.8-1.79-1.42-1.42zM12 6a6 6 0 100 12 6 6 0 000-12z"/>
                </svg>
            ) : (
                // Moon icon for switching to dark
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"
                     focusable="false">
                    <path fill="currentColor"
                          d="M12.74 2.25a.75.75 0 01.86.98A8.5 8.5 0 1019.5 13.9a.75.75 0 01.98.86A10 10 0 1112.74 2.25z"/>
                </svg>
            )}
        </button>
    );
}


