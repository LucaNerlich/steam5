import type {NextConfig} from "next";

const nextConfig: NextConfig = {
    compiler: {
        // Strip only the noisy log levels, not every console.* call: keep error/warn so
        // server-side failure paths (e.g. the sitemap's "archive dates unavailable" warnings)
        // remain visible in compiled production output.
        removeConsole:
            process.env.NODE_ENV === 'production' ? {exclude: ['error', 'warn']} : false,
    },
    reactCompiler: true,
    experimental: {
        turbopackFileSystemCacheForDev: true,
        turbopackRustReactCompiler: true,
    },
    images: {
        remotePatterns: [
            // All Steam CDN subdomains (shared./store./avatars./cdn./cdn-ak.cloudflare. …)
            {
                protocol: 'https',
                hostname: '**.steamstatic.com',
            },
            {
                protocol: 'https',
                hostname: 'steamcdn-a.akamaihd.net',
            },
        ],
    },
    async redirects(): Promise<{ source: string; destination: string; permanent: boolean }[]> {
        return [
            {
                source: '/',
                destination: '/review-guesser/1',
                permanent: true,
            },
            {
                source: '/rg',
                destination: '/review-guesser/1',
                permanent: true,
            },
            {
                source: '/rg/lb',
                destination: '/review-guesser/leaderboard',
                permanent: true,
            },
            {
                source: '/leaderboard',
                destination: '/review-guesser/leaderboard',
                permanent: true,
            },
            {
                source: '/review-guesser/hardest',
                destination: '/review-guesser/leaderboard/hardest',
                permanent: true,
            },
            {
                // The legacy singular season URL duplicated the whole page (its `export *`
                // could not shadow the local exports, so the copy below it was what ran).
                // One redirect replaces it.
                source: '/review-guesser/season/:seasonNumber',
                destination: '/review-guesser/seasons/:seasonNumber',
                permanent: true,
            },

        ];
    }
};

export default nextConfig;
