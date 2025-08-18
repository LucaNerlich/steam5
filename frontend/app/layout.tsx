import type {Metadata} from "next";
import "@/styles/globals.css";
import UmamiAnalytics from "@/components/UmamiAnalytics";
import Header from "@/components/Header";
import Footer from "@/components/Footer";
import localFont from "next/font/local";

const krypton = localFont({
    src: [
        {path: "./fonts/krypton/MonaspaceKrypton-Regular.woff2", weight: "400", style: "normal"},
        {path: "./fonts/krypton/MonaspaceKrypton-Bold.woff2", weight: "700", style: "normal"},
    ],
    display: 'swap',
    variable: '--font-krypton'
});

const neon = localFont({
    src: [
        {path: "./fonts/neon/MonaspaceNeon-Regular.woff2", weight: "400", style: "normal"},
        {path: "./fonts/neon/MonaspaceNeon-Bold.woff2", weight: "700", style: "normal"},
    ],
    display: 'swap',
    variable: '--font-neon',
    preload: false, // load Neon on demand when toggled
});


const description = 'Steam Review Guessing Game';

export const metadata: Metadata = {
    title: 'Steam5',
    description: description,
    keywords: 'Review, Steam, Guessing Game',
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
    metadataBase: new URL(process.env.NEXT_PUBLIC_DOMAIN || 'https://steam5.org'),
    openGraph: {
        emails: 'luca.nerlich@gmail.com',
        title: 'Steam5',
        description: description,
        url: '/',
        siteName: 'Steam5.org',
        locale: 'de_DE',
        type: 'website',
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Steam5',
        description: description,
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

export default function RootLayout({
                                       children,
                                   }: Readonly<{
    children: React.ReactNode;
}>) {
    return (
        <html lang="en" className={`${krypton.variable} ${neon.variable}`}>
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
