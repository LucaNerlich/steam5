"use client";

import {useEffect, useSyncExternalStore} from "react";
import {isTheme, THEME_IDS, type Theme} from "@/lib/theme/themes";

type ThemeOption = {
    id: Theme;
    label: string;
};

const THEME_LABELS: Record<Theme, string> = {
    light: "Light",
    dark: "Dark",
    oled: "OLED",
    hacker: "Hacker",
    rainbow: "Rainbow",
    night: "Night",
};

const THEME_OPTIONS: ThemeOption[] = THEME_IDS.map((id) => ({
    id,
    label: THEME_LABELS[id],
}));

let currentTheme: Theme = "light";
const listeners = new Set<() => void>();

function notifyListeners() {
    for (const l of listeners) l();
}

function safeGetStoredTheme(): string | null {
    if (typeof window === "undefined") return null;
    try {
        return window.localStorage.getItem("theme");
    } catch {
        return null;
    }
}

function safeSetStoredTheme(theme: Theme) {
    if (typeof window === "undefined") return;
    try {
        window.localStorage.setItem("theme", theme);
    } catch {
        // Ignore storage errors (private mode/restricted contexts).
    }
}

function safeClearStoredTheme() {
    if (typeof window === "undefined") return;
    try {
        window.localStorage.removeItem("theme");
    } catch {
        // Ignore storage errors (private mode/restricted contexts).
    }
}

function readThemeFromDOM(): Theme {
    if (typeof window === "undefined") return "light";
    const rootTheme = document.documentElement.getAttribute("data-theme");
    if (isTheme(rootTheme)) return rootTheme;
    const stored = safeGetStoredTheme();
    if (stored && isTheme(stored)) return stored;
    if (stored) safeClearStoredTheme();
    return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? "dark" : "light";
}

function applyTheme(theme: Theme, persist = true) {
    currentTheme = theme;
    const root = document.documentElement;
    root.setAttribute("data-theme", theme);
    if (persist) {
        safeSetStoredTheme(theme);
    }
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
            const stored = safeGetStoredTheme();
            // Only follow system preference when no explicit theme choice is stored
            if (stored && !isTheme(stored)) {
                safeClearStoredTheme();
            }
            if (!stored || !isTheme(stored)) {
                applyTheme(media.matches ? "dark" : "light", false);
            }
        };
        media.addEventListener("change", handler);
        return () => media.removeEventListener("change", handler);
    }, []);

    return (
        <select
            aria-label="Color theme"
            className="theme-select"
            value={theme}
            onChange={(e) => {
                const value = e.target.value;
                if (isTheme(value)) applyTheme(value);
            }}
        >
            {THEME_OPTIONS.map(option => (
                <option key={option.id} value={option.id}>
                    {option.label}
                </option>
            ))}
        </select>
    );
}


