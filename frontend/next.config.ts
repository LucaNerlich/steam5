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
};

export default nextConfig;
