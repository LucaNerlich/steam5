import {Routes} from './routes';
import {MetadataRoute} from 'next';

/**
 * Resolves the backend origin for the sitemap fetch. In production the configured
 * origin must use HTTPS — absent or plaintext configuration fails closed (null)
 * rather than falling back to plaintext localhost HTTP. Local dev may keep using
 * the localhost backend.
 */
function resolveBackendOrigin(): string | null {
    const configured = process.env.NEXT_PUBLIC_API_DOMAIN?.trim();
    if (configured) {
        try {
            const url = new URL(configured);
            return url.protocol === 'https:' ? url.origin : null;
        } catch {
            return null;
        }
    }
    return process.env.NODE_ENV === 'production' ? null : 'http://localhost:8080';
}

/** Loads the set of dates that actually have an archived challenge; empty on failure. */
async function loadExistingArchiveDates(): Promise<Set<string>> {
    const backend = resolveBackendOrigin();
    if (!backend) return new Set();
    try {
        const res = await fetch(`${backend}/api/review-game/days?limit=5000`, {
            headers: {accept: 'application/json'},
            signal: AbortSignal.timeout(5000),
        });
        if (!res.ok) return new Set();
        const data: unknown = await res.json();
        if (!Array.isArray(data)) return new Set();
        return new Set(data.filter((d): d is string => typeof d === 'string'));
    } catch {
        return new Set();
    }
}

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
    const baseUrl = (process.env.NEXT_PUBLIC_DOMAIN || 'https://steam5.org').replace(/\/$/, '');
    const existingDates = await loadExistingArchiveDates();

    // These routes redirect (308/307) and must not appear in the sitemap —
    // search engines would waste crawl budget on the redirect hops.
    const redirectingRoutes = new Set(['/', '/review-guesser', '/review-guesser/random']);
    const routes = Object.values(Routes).filter(
        (route): route is string => typeof route === 'string' && !redirectingRoutes.has(route)
    );
    const now = new Date();
    const staticPageFrequency = 'weekly' as const;

    // Build list of archive dates from 2025-08-14 (inclusive) until yesterday (UTC),
    // limited to dates the backend actually has a challenge for. Today's challenge
    // is served by /review-guesser/*; its archive copy duplicates the live game
    // and is excluded.
    const startDate = new Date(Date.UTC(2025, 7, 14)); // Aug is 7 (0-based)
    const yesterdayUtc = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() - 1));
    const reviewGuesserArchive: string[] = [];
    for (let d = new Date(startDate); d <= yesterdayUtc; d = new Date(d.getTime() + 24 * 60 * 60 * 1000)) {
        const date = d.toISOString().slice(0, 10); // yyyy-mm-dd
        if (existingDates.has(date)) {
            reviewGuesserArchive.push(date);
        }
    }

    return [
        // Existing static/dynamic routes
        ...routes.map((route) => ({
            url: `${baseUrl}${route}`,
            lastModified: now,
            changeFrequency: staticPageFrequency,
            priority: route === '/review-guesser/1' ? 1 : 0.8,
            alternates: {
                languages: {
                    en: `${baseUrl}${route}`,
                    'x-default': `${baseUrl}${route}`,
                },
            },
        })),
        // Archive entries for each date
        ...reviewGuesserArchive.map((date) => ({
            url: `${baseUrl}/review-guesser/archive/${date}`,
            // Archive pages are immutable once created; keep lastModified to the specific date
            lastModified: new Date(date),
            changeFrequency: 'never' as const,
            priority: 0.6,
            alternates: {
                languages: {
                    en: `${baseUrl}/review-guesser/archive/${date}`,
                    'x-default': `${baseUrl}/review-guesser/archive/${date}`,
                },
            },
        })),
    ];
}
