import type {NextConfig} from "next";

const nextConfig: NextConfig = {
    experimental: {
        reactCompiler: true,
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
                permanent: false,
            },

        ];
    }
};

export default nextConfig;
