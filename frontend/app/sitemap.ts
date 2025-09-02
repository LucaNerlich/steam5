import {Routes} from './routes';
import {MetadataRoute} from 'next';

export default function sitemap(): MetadataRoute.Sitemap {
    const baseUrl = (process.env.NEXT_PUBLIC_DOMAIN || 'https://steam5.org').replace(/\/$/, '');

    const routes = Object.values(Routes);
    const now = new Date();
    const staticPageFrequency = 'weekly' as const;

    // Build list of archive dates from 2025-08-14 (inclusive) until today (UTC)
    const startDate = new Date(Date.UTC(2025, 7, 14)); // Aug is 7 (0-based)
    const todayUtc = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
    const reviewGuesserArchive: string[] = [];
    for (let d = new Date(startDate); d <= todayUtc; d = new Date(d.getTime() + 24 * 60 * 60 * 1000)) {
        reviewGuesserArchive.push(d.toISOString().slice(0, 10)); // yyyy-mm-dd
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
