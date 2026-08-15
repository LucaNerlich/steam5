import {Routes} from './routes';
import {MetadataRoute} from 'next';

/** Loads the set of dates that actually have an archived challenge; empty on failure. */
async function loadExistingArchiveDates(): Promise<Set<string>> {
    try {
        const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
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

    const routes = Object.values(Routes).filter((route): route is string => typeof route === 'string');
    const now = new Date();
    const staticPageFrequency = 'weekly' as const;

    // Build list of archive dates from 2025-08-14 (inclusive) until today (UTC),
    // limited to dates the backend actually has a challenge for.
    const startDate = new Date(Date.UTC(2025, 7, 14)); // Aug is 7 (0-based)
    const todayUtc = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
    const reviewGuesserArchive: string[] = [];
    for (let d = new Date(startDate); d <= todayUtc; d = new Date(d.getTime() + 24 * 60 * 60 * 1000)) {
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
            priority: route === '/' ? 1 : 0.8,
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
