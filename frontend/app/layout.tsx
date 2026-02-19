import type {Metadata} from "next";
import Script from "next/script";
import { cookies } from "next/headers";
import "@/styles/globals.css";
import UmamiAnalytics from "@/components/UmamiAnalytics";
import Header from "@/components/Header";
import Footer from "@/components/Footer";
import { AuthProvider } from "@/contexts/AuthContext";
import localFont from "next/font/local";

const krypton = localFont({
    src: [
        {path: "./fonts/MonaspaceKryptonVar.woff2"},
    ],
    display: 'swap',
    variable: '--font-krypton'
});

const neon = localFont({
    src: [
        {path: "./fonts/MonaspaceNeonVar.woff2"},
    ],
    display: 'swap',
    variable: '--font-neon',
    preload: false,
});

const argon = localFont({
    src: [
        {path: "./fonts/MonaspaceArgonVar.woff2"},
    ],
    display: 'swap',
    variable: '--font-argon',
    preload: false,
});

const radon = localFont({
    src: [
        {path: "./fonts/MonaspaceRadonVar.woff2"},
    ],
    display: 'swap',
    variable: '--font-radon',
    preload: false,
});

const xenon = localFont({
    src: [
        {path: "./fonts/MonaspaceXenonVar.woff2"},
    ],
    display: 'swap',
    variable: '--font-xenon',
    preload: false,
});

const space = localFont({
    src: [
        {path: "./fonts/SpaceMono-Regular.woff2"},
    ],
    display: 'swap',
    variable: '--font-space',
    preload: false,
});

const description = 'Daily Steam review guessing game — guess review counts, climb leaderboards, and share your results.';
const base = (process.env.NEXT_PUBLIC_DOMAIN || "https://steam5.org").replace(/\/$/, "");
let origin = base;
try {
    origin = new URL(base).origin;
} catch {
    // ignore, fallback to base as-is
}
const logoUrl = `${origin}/icon.svg`;
const websiteSchema = {
    '@context': 'https://schema.org',
    '@type': 'WebSite',
    name: 'Steam5',
    url: origin,
    description: 'Daily Steam review guessing game — guess review counts and climb the leaderboard.',
    inLanguage: 'en',
    publisher: {
        '@type': 'Organization',
        name: 'Steam5',
        url: origin,
    },
};
const organizationSchema = {
    '@context': 'https://schema.org',
    '@type': 'Organization',
    name: 'Steam5',
    url: origin,
    logo: logoUrl,
    sameAs: [
        'https://github.com/LucaNerlich/steam5'
    ],
    email: 'luca.nerlich@gmail.com'
};

export const metadata: Metadata = {
    title: {
        default: 'Steam5',
        template: '%s | Steam5',
    },
    description: description,
    keywords: [
        'Steam',
        'Steam reviews',
        'guessing game',
        'daily game',
        'review counts',
        'leaderboard',
        'indie game',
        'gaming trivia'
    ],
    creator: 'Luca Nerlich',
    publisher: 'Luca Nerlich',
    formatDetection: {
        email: true,
        address: true,
        telephone: false,
    },
    alternates: {
        canonical: `/`,
    },
    metadataBase: new URL((process.env.NEXT_PUBLIC_DOMAIN || 'https://steam5.org').replace(/\/$/, '')),
    openGraph: {
        emails: 'luca.nerlich@gmail.com',
        title: 'Steam5',
        description: description,
        url: '/',
        siteName: 'Steam5',
        locale: 'en_US',
        images: ['/opengraph-image'],
        type: 'website',
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Steam5',
        description: description,
        images: ['/opengraph-image'],
    },
    applicationName: 'Steam5',
    abstract: description,
    category: 'Games, Technology',
    referrer: 'same-origin',
    generator: 'Next.js',
    robots: {
        index: true,
        follow: true,
        googleBot: {
            index: true,
            follow: true,
            'max-video-preview': -1,
            'max-image-preview': 'large',
            'max-snippet': -1,
        },
    },
}

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

async function resolveAuth() {
    try {
        const cookieStore = await cookies();
        const token = cookieStore.get('s5_token')?.value;

        if (!token) {
            return { isSignedIn: false };
        }

        // Check auth with backend - use /api/auth/validate endpoint
        const res = await fetch(
            `${BACKEND_ORIGIN}/api/auth/validate?token=${encodeURIComponent(token)}`,
            {
                headers: { 'accept': 'application/json' },
                next: { revalidate: 60 }, // Cache for 1 minute
            }
        );

        if (res.status === 401) {
            return { isSignedIn: false };
        }

        if (!res.ok) {
            return { isSignedIn: false };
        }

        const data = await res.json();
        return {
            isSignedIn: Boolean(data?.valid),  // Changed from data?.signedIn
            steamId: data?.steamId || null,
        };
    } catch (error) {
        // During build, connection refused is expected
        if (error && typeof error === 'object' && 'cause' in error) {
            const cause = error.cause as any;
            if (cause?.code === 'ECONNREFUSED') {
                // Silently return false during build
                return { isSignedIn: false };
            }
        }
        console.error('Auth resolution error:', error);
        return { isSignedIn: false };
    }
}

export default async function RootLayout({
                                       children,
                                   }: Readonly<{
    children: React.ReactNode;
}>) {
    const authState = await resolveAuth();
    return (
        <html
            lang="en"
            className={`${krypton.variable} ${neon.variable} ${argon.variable} ${radon.variable} ${xenon.variable} ${space.variable}`}
            suppressHydrationWarning
        >
        <head>
            <Script src="/theme-init.js" strategy="beforeInteractive"/>
            <link rel="preconnect" href={origin}/>
            <link rel="dns-prefetch" href={origin}/>
            <script
                type="application/ld+json"
                dangerouslySetInnerHTML={{__html: JSON.stringify(websiteSchema)}}
            />
            <script
                type="application/ld+json"
                dangerouslySetInnerHTML={{__html: JSON.stringify(organizationSchema)}}
            />
        </head>
        <body>
        <AuthProvider initialAuth={authState}>
            <Header/>
            <main>
                {children}
            </main>
            <Footer/>
            <UmamiAnalytics/>
        </AuthProvider>
        </body>
        </html>
    );
}
