import type {Metadata} from "next";
import "@/styles/globals.css";
import UmamiAnalytics from "@/components/UmamiAnalytics";
import Header from "@/components/Header";
import Footer from "@/components/Footer";
import { AuthProvider } from "@/contexts/AuthContext";
import { serializeJsonLd } from "@/lib/seo";
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

const pixelSquare = localFont({
    src: [
        {path: "./fonts/GeistPixel-Square.woff2"},
    ],
    display: 'swap',
    variable: '--font-pixel-square',
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

const metadataBase = (() => {
    try {
        return new URL(origin);
    } catch {
        // Invalid NEXT_PUBLIC_DOMAIN (e.g. missing protocol) — omit metadataBase
        // instead of crashing every page at build/SSR time.
        return undefined;
    }
})();

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
    metadataBase,
    openGraph: {
        emails: 'luca.nerlich@gmail.com',
        title: 'Steam5',
        description: description,
        url: '/',
        siteName: 'Steam5',
        locale: 'en_US',
        images: ['/og'],
        type: 'website',
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Steam5',
        description: description,
        images: ['/og'],
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

// Classic blocking script in <head>: next/script beforeInteractive is queued behind the
// async runtime, so dark-mode users painted light first. Reads localStorage, then the
// theme cookie, then the OS preference.
const THEME_BOOTSTRAP = `(function(){try{var t=localStorage.getItem('theme');if(t!=='light'&&t!=='dark'){var m=document.cookie.match(/(?:^|; )theme=(dark|light)/);t=m?m[1]:(window.matchMedia&&window.matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light');}if(t==='dark'){document.documentElement.setAttribute('data-theme','dark');}else{document.documentElement.removeAttribute('data-theme');}}catch(e){if(window.matchMedia&&window.matchMedia('(prefers-color-scheme: dark)').matches){document.documentElement.setAttribute('data-theme','dark');}}})();`;

export default function RootLayout({
                                       children,
                                   }: Readonly<{
    children: React.ReactNode;
}>) {
    // Signed-out initial state: reading cookies/headers here opted every route into dynamic
    // rendering and neutralized each page's `revalidate`. AuthProvider revalidates
    // /api/auth/me on mount, and theme-init.js applies the theme cookie before hydration.
    return (
        <html
            data-scroll-behavior="smooth"
            lang="en"
            className={`${krypton.variable} ${neon.variable} ${argon.variable} ${radon.variable} ${xenon.variable} ${space.variable} ${pixelSquare.variable}`}
            suppressHydrationWarning
        >
        <head>
            <script dangerouslySetInnerHTML={{__html: THEME_BOOTSTRAP}}/>
        </head>
        <body>
        <script
            type="application/ld+json"
            dangerouslySetInnerHTML={{__html: serializeJsonLd(websiteSchema)}}
        />
        <script
            type="application/ld+json"
            dangerouslySetInnerHTML={{__html: serializeJsonLd(organizationSchema)}}
        />
        <link rel="preconnect" href={origin}/>
        <link rel="dns-prefetch" href={origin}/>
        <AuthProvider initialAuth={{isSignedIn: false}}>
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
