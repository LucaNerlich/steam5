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
