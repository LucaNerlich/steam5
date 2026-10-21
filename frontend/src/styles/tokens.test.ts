// Token-level guard for the contrast fixes from the AA pass (issues #328/#329/#344/#345).
// Plain fs + regex + WCAG math; no colour libraries, and prism-free of any DOM usage.
//
// Two checks:
//  1. Every var(--color-*) reference in src/styles/** resolves to a definition or carries
//     its own fallback (a hung reference silently renders inherited colour).
//  2. The fill/text pairs restored by the AA pass meet WCAG AA 4.5:1 in BOTH themes.
//     Values are read from the css files themselves, so a fill or ink that regresses
//     below the threshold fails here.
//
// Known limitation (by design): this cannot see CSS specificity, so the :visited
// guards (a.btn-cta:visited, a.btn-success:visited, a.not-found__button:visited —
// all (0,2,1) outranking a:visited's (0,1,1)) are verified by reading the cascade,
// not by this test.
import {readFileSync, readdirSync} from 'node:fs';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {describe, expect, it} from 'vitest';

const STYLES_DIR = fileURLToPath(new URL('.', import.meta.url));
const MIN_CONTRAST = 4.5;

// ── colour math ────────────────────────────────────────────────────────────────

type Srgb = [number, number, number]; // gamma-encoded, 0..1

function linearize(c: number): number {
    return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
}

function gammaEncode(c: number): number {
    return c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055;
}

function relativeLuminance(srgb: Srgb): number {
    const [r, g, b] = srgb.map(linearize) as Srgb;
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrastRatio(foreground: Srgb, background: Srgb): number {
    const l1 = relativeLuminance(foreground);
    const l2 = relativeLuminance(background);
    const [hi, lo] = l1 >= l2 ? [l1, l2] : [l2, l1];
    return (hi + 0.05) / (lo + 0.05);
}

function oklchToSrgb(text: string): Srgb {
    const parts = text.slice('oklch('.length, text.length - 1)
        .split(/\s+/)
        .filter(Boolean)
        .map(Number)
        .filter((n) => !Number.isNaN(n));
    const [L, C, H] = parts;
    const hue = (H * Math.PI) / 180;
    const a = C * Math.cos(hue);
    const b = C * Math.sin(hue);
    const l_ = L + 0.3963377774 * a + 0.2158037573 * b;
    const m_ = L - 0.1055613458 * a - 0.0638541728 * b;
    const s_ = L - 0.0894841775 * a - 1.2914855480 * b;
    const l = l_ ** 3;
    const m = m_ ** 3;
    const s = s_ ** 3;
    const linear = [
        4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
    ].map((c) => Math.min(1, Math.max(0, c))) as Srgb;
    return linear.map(gammaEncode) as Srgb;
}

function hexToSrgb(text: string): Srgb {
    const digits = text.length - 1 === 3
        ? text.slice(1).split('').map((c) => c + c).join('')
        : text.slice(1);
    return [0, 2, 4].map((i) => parseInt(digits.slice(i, i + 2), 16) / 255) as Srgb;
}

// Full var(...) call text starting at s in css (handles nested parens).
function varCallFrom(css: string, start: number): string {
    let depth = 0;
    for (let i = start; i < css.length; i++) {
        if (css[i] === '(') depth++;
        else if (css[i] === ')' && --depth === 0) return css.slice(start, i + 1);
    }
    throw new Error('unterminated var()');
}

// var(--name, fallback?) — name plus optional fallback (may itself hold nested parens).
function parseVarCall(text: string): {name: string; fallback: string} {
    const open = text.indexOf('(');
    let depth = 0;
    let firstComma = -1;
    let end = -1;
    for (let i = open; i < text.length; i++) {
        if (text[i] === '(') depth++;
        else if (text[i] === ')') {
            depth--;
            if (depth === 0) {
                end = i;
                break;
            }
        } else if (text[i] === ',' && depth === 1 && firstComma === -1) firstComma = i;
    }
    return {
        name: text.slice(open + 1, firstComma === -1 ? end : firstComma).trim(),
        fallback: firstComma === -1 ? '' : text.slice(firstComma + 1, end).trim(),
    };
}

function splitTopLevel(text: string): string[] {
    const parts: string[] = [];
    let depth = 0;
    let start = 0;
    for (let i = 0; i < text.length; i++) {
        if (text[i] === '(') depth++;
        else if (text[i] === ')') depth--;
        else if (text[i] === ',' && depth === 0) {
            parts.push(text.slice(start, i));
            start = i + 1;
        }
    }
    parts.push(text.slice(start));
    return parts;
}

// color-mix(in srgb, A p%, B) — browsers blend the gamma-encoded sRGB channels here.
function colorMixToSrgb(text: string, tokens: Record<string, string>): Srgb {
    const inner = text.slice('color-mix('.length, text.length - 1);
    const [space, leftRaw, rightRaw] = splitTopLevel(inner).map((p) => p.trim());
    if (!/^in\s+srgb$/.test(space) || !leftRaw || !rightRaw) {
        throw new Error(`unsupported color-mix syntax: ${text}`);
    }
    const [leftColor, leftWeight] = weightedColor(leftRaw);
    const [rightColor, rightWeight] = weightedColor(rightRaw);
    // An omitted percentage mirrors 100 minus the explicit one; a bare mix is 50/50.
    const p1 = leftWeight ?? (rightWeight === undefined ? 50 : 100 - rightWeight);
    const p2 = rightWeight ?? (leftWeight === undefined ? 50 : 100 - leftWeight);
    const w1 = p1 / (p1 + p2);
    const w2 = p2 / (p1 + p2);
    const a = resolveColor(leftColor, tokens);
    const b = resolveColor(rightColor, tokens);
    return a.map((c, i) => Math.min(1, Math.max(0, c * w1 + b[i] * w2))) as Srgb;
}

// "colour 12%" -> [colour, 12]; a missing percentage comes back undefined.
function weightedColor(part: string): [string, number | undefined] {
    const m = part.match(/^(.*?)\s*([\d.]+)%\s*$/);
    if (!m) return [part, undefined];
    return [m[1], Number(m[2])];
}

function resolveColor(expr: string, tokens: Record<string, string>): Srgb {
    const s = expr.trim();
    if (s.startsWith('var(')) {
        const {name, fallback} = parseVarCall(s);
        const value = tokens[name] || fallback;
        if (!value) throw new Error(`colour token ${name} has no definition and no fallback`);
        return resolveColor(value, tokens);
    }
    if (s.startsWith('color-mix(')) return colorMixToSrgb(s, tokens);
    if (s.startsWith('oklch(')) return oklchToSrgb(s);
    if (s.startsWith('#')) return hexToSrgb(s);
    throw new Error(`unsupported colour value: ${s}`);
}

// ── css reading ───────────────────────────────────────────────────────────────

function withoutComments(css: string): string {
    return css.replace(/\/\*[\s\S]*?\*\//g, '');
}

function cssFiles(dir: string): string[] {
    const files: string[] = [];
    for (const entry of readdirSync(dir, {withFileTypes: true})) {
        const path = join(dir, entry.name);
        if (entry.isDirectory()) files.push(...cssFiles(path));
        else if (entry.name.endsWith('.css')) files.push(path);
    }
    return files;
}

// Body of the first {...} block whose opening brace follows `from`.
function blockFrom(css: string, from: number): string {
    const open = css.indexOf('{', from);
    if (open === -1) throw new Error('no block found');
    let depth = 0;
    for (let i = open; i < css.length; i++) {
        if (css[i] === '{') depth++;
        else if (css[i] === '}') {
            depth--;
            if (depth === 0) return css.slice(open + 1, i);
        }
    }
    throw new Error('unbalanced block');
}

// background + colour declarations of the main rule for a selector token; the first
// occurrence carrying both wins, so :hover/:visited companions don't shadow it.
function declarationsOf(path: string, selector: string): {background?: string; color: string} {
    const css = withoutComments(readFileSync(path, 'utf8'));
    let index = 0;
    while ((index = css.indexOf(selector, index)) !== -1) {
        const body = blockFrom(css, index);
        const background = body.match(/background\s*:\s*([^;]+);/)?.[1];
        // Word boundary: must not match inside "border-color:".
        const color = body.match(/(?<![-\w])color\s*:\s*([^;]+);/)?.[1];
        if (color) return {background, color};
        index += selector.length;
    }
    throw new Error(`no background+color rule for ${selector} in ${path}`);
}

// ── tokens ────────────────────────────────────────────────────────────────────

const VARIABLES = withoutComments(readFileSync(join(STYLES_DIR, 'variables.css'), 'utf8'));
const darkScopeAt = VARIABLES.indexOf("html[data-theme='dark']");
if (darkScopeAt === -1) throw new Error('dark theme block missing in variables.css');

function tokenMap(block: string): Record<string, string> {
    const tokens: Record<string, string> = {};
    for (const m of block.matchAll(/(--color-[a-z0-9-]+)\s*:\s*([^;]+);/g)) {
        tokens[m[1]] = m[2].trim();
    }
    return tokens;
}

const lightTokens = tokenMap(VARIABLES.slice(0, darkScopeAt));
const darkTokens = tokenMap(VARIABLES.slice(darkScopeAt));
const darkThemeTokens = {...lightTokens, ...darkTokens};
const allDefinedTokens = new Set([...Object.keys(lightTokens), ...Object.keys(darkTokens)]);

// ── 1. no hung palette reference ──────────────────────────────────────────────

describe('css colour tokens', () => {
    it('every var(--color-*) reference has a definition or a fallback', () => {
        const hung: string[] = [];
        for (const path of cssFiles(STYLES_DIR)) {
            const css = withoutComments(readFileSync(path, 'utf8'));
            for (const m of css.matchAll(/var\(--color-[a-z0-9-]+/g)) {
                const {name, fallback} = parseVarCall(varCallFrom(css, m.index));
                if (allDefinedTokens.has(name) || fallback) continue;
                hung.push(`${path.slice(STYLES_DIR.length)}: ${name}`);
            }
        }
        expect(hung, 'var() references without definition or fallback').toEqual([]);
    });
});

// ── 2. AA contrast on the pairs restored by the double pass ───────────────────

const referencePairs: Array<{label: string; file: string; selector: string; background?: string}> = [
    {label: '.btn-cta fill/text', file: 'shared.css', selector: '.btn-cta'},
    {label: '.btn-success fill/text', file: 'shared.css', selector: '.btn-success'},
    {label: '.not-found__button fill/text', file: 'components/not-found.css', selector: '.not-found__button'},
    {
        label: '.archive-list__month-chip--active fill/text',
        file: 'components/archive-list.css',
        selector: '.archive-list__month-chip--active',
    },
    ...['exact', 'close', 'near', 'far'].map((tier) => ({
        label: `.result-header--${tier} fill/text`,
        file: 'components/reviewRoundResult.css',
        selector: `.result-header--${tier}`,
    })),
    ...['exact', 'close', 'near', 'far', 'actual'].map((tier) => ({
        label: `.result-chip--${tier} fill/text`,
        file: 'components/reviewRoundResult.css',
        selector: `.result-chip--${tier}`,
    })),
    {
        label: '.season-card__link text/surface',
        file: 'components/seasons.css',
        selector: '.season-card__link',
        background: 'var(--color-surface)',
    },
];

describe('background/text pairs meet WCAG AA 4.5:1', () => {
    const themes: Array<[string, Record<string, string>]> = [
        ['light', lightTokens],
        ['dark', darkThemeTokens],
    ];
    for (const [theme, tokens] of themes) {
        for (const pair of referencePairs) {
            it(`[${theme}] ${pair.label}`, () => {
                const declarations = declarationsOf(join(STYLES_DIR, pair.file), pair.selector);
                const backgroundValue = pair.background ?? declarations.background;
                if (!backgroundValue) throw new Error(`no background for ${pair.selector}`);
                const background = resolveColor(backgroundValue, tokens);
                const text = resolveColor(declarations.color, tokens);
                const ratio = contrastRatio(text, background);
                expect(
                    ratio,
                    `${pair.label} in ${theme} theme: ${ratio.toFixed(2)}:1 is below AA 4.5:1`,
                ).toBeGreaterThanOrEqual(MIN_CONTRAST);
            });
        }
    }
});

describe('performance status text meets WCAG AA 4.5:1 on the surface', () => {
    const themes: Array<[string, Record<string, string>]> = [
        ['light', lightTokens],
        ['dark', darkThemeTokens],
    ];
    for (const [theme, tokens] of themes) {
        for (const token of ['--color-warning', '--color-danger', '--color-success-text']) {
            it(`[${theme}] ${token} on --color-surface`, () => {
                const surface = resolveColor('var(--color-surface)', tokens);
                const ink = resolveColor(`var(${token})`, tokens);
                const ratio = contrastRatio(ink, surface);
                expect(ratio, `${token} in ${theme}: ${ratio.toFixed(2)}:1`).toBeGreaterThanOrEqual(MIN_CONTRAST);
            });
        }
    }
});
describe('bucket callout track', () => {
    it('keeps the track translucent without dimming the child fill', () => {
        const css = withoutComments(readFileSync(join(STYLES_DIR, 'components/profile.css'), 'utf8'));
        const body = blockFrom(css, css.indexOf('.bucket-callout__bar-bg'));
        expect(body).toContain('color-mix');
        expect(body).not.toMatch(/(?<![-\w])opacity\s*:/);
    });
});
