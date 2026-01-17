import type {NextConfig} from "next";

const nextConfig: NextConfig = {
    compiler: {
        removeConsole: process.env.NODE_ENV === 'production',
    },
    reactCompiler: true,
    experimental: {
        turbopackFileSystemCacheForDev: true,
    },
    images: {
        remotePatterns: [
            {
                protocol: 'https',
                hostname: 'shared.akamai.steamstatic.com',
            },
            {
                protocol: 'https',
                hostname: 'store.akamai.steamstatic.com',
            },
            {
                protocol: 'https',
                hostname: 'avatars.fastly.steamstatic.com',
            },
            {
                protocol: 'https',
                hostname: 'avatars.steamstatic.com',
            },
            {
                protocol: 'https',
                hostname: 'avatars.cloudflare.steamstatic.com',
            },
            {
                protocol: 'https',
                hostname: 'avatars.akamai.steamstatic.com',
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
                destination: '/review-guesser',
                permanent: true,
            },
            {
                source: '/leaderboard',
                destination: '/review-guesser/leaderboard',
                permanent: true,
            },

        ];
    }
};

export default nextConfig;
