"use client";

import {useEffect, useState} from "react";

function getPreferredTheme(): "light" | "dark" {
    if (typeof window === "undefined") return "light";
    const stored = window.localStorage.getItem("theme");
    if (stored === "light" || stored === "dark") return stored;
    return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
        ? "dark"
        : "light";
}

export default function ThemeToggle() {
    const [theme, setTheme] = useState<"light" | "dark">(getPreferredTheme());

    useEffect(() => {
        const root = document.documentElement;
        if (theme === "dark") {
            root.setAttribute("data-theme", "dark");
        } else {
            root.removeAttribute("data-theme");
        }
        window.localStorage.setItem("theme", theme);
    }, [theme]);

    useEffect(() => {
        const media = window.matchMedia('(prefers-color-scheme: dark)');
        const handler = () => {
            const stored = window.localStorage.getItem("theme");
            if (stored !== "light" && stored !== "dark") {
                setTheme(media.matches ? "dark" : "light");
            }
        };
        media.addEventListener("change", handler);
        return () => media.removeEventListener("change", handler);
    }, []);

    return (
        <button
            className="theme-toggle"
            aria-label="Toggle color theme"
            onClick={() => setTheme(prev => prev === "dark" ? "light" : "dark")}
        >
            {theme === "dark" ? "Light Mode" : "Dark Mode"}
        </button>
    );
}


