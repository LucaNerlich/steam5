export const THEME_IDS = [
    "light",
    "dark",
    "oled",
    "hacker",
    "rainbow",
    "night",
] as const;

export type Theme = (typeof THEME_IDS)[number];

const THEME_SET = new Set<string>(THEME_IDS);

export function isTheme(value: string | null): value is Theme {
    return typeof value === "string" && THEME_SET.has(value);
}
