import type {Metadata} from "next";
import "@/styles/globals.css";
import UmamiAnalytics from "@/components/UmamiAnalytics";
import Header from "@/components/Header";
import Footer from "@/components/Footer";
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


const description = 'Daily Steam review guessing game — guess review counts, climb leaderboards, and share your results.';

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
    themeColor: '#0b1220',
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

export default function RootLayout({
                                       children,
                                   }: Readonly<{
    children: React.ReactNode;
}>) {
    return (
        <html lang="en"
              className={`${krypton.variable} ${neon.variable} ${argon.variable} ${radon.variable} ${xenon.variable}`}>
        <body>
        <Header/>
        <main>
            {children}
        </main>
        <Footer/>
        <UmamiAnalytics/>
        </body>
        </html>
    );
}
