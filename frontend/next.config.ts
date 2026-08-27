import type {NextConfig} from "next";

const nextConfig: NextConfig = {
    compiler: {
        removeConsole: process.env.NODE_ENV === 'production',
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

        ];
    }
};

export default nextConfig;
