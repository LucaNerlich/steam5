import type {Metadata} from "next";
import "@/styles/globals.css";
import UmamiAnalytics from "@/components/UmamiAnalytics";
import Header from "@/components/Header";
import Footer from "@/components/Footer";
import localFont from "next/font/local";

const krypton = localFont({
    src: [
        {path: "./fonts/krypton/MonaspaceKrypton-Light.woff2", weight: "300", style: "normal"},
        {path: "./fonts/krypton/MonaspaceKrypton-Regular.woff2", weight: "400", style: "normal"},
        {path: "./fonts/krypton/MonaspaceKrypton-Medium.woff2", weight: "500", style: "normal"},
        {path: "./fonts/krypton/MonaspaceKrypton-Bold.woff2", weight: "700", style: "normal"},
        {path: "./fonts/krypton/MonaspaceKrypton-ExtraBold.woff2", weight: "800", style: "normal"},
        {path: "./fonts/krypton/MonaspaceKrypton-Italic.woff2", weight: "400", style: "italic"},
        {path: "./fonts/krypton/MonaspaceKrypton-BoldItalic.woff2", weight: "700", style: "italic"},
    ],
    display: 'swap',
    variable: '--font-krypton'
});

const neon = localFont({
    src: [
        {path: "./fonts/neon/MonaspaceNeon-Light.woff2", weight: "300", style: "normal"},
        {path: "./fonts/neon/MonaspaceNeon-Regular.woff2", weight: "400", style: "normal"},
        {path: "./fonts/neon/MonaspaceNeon-Medium.woff2", weight: "500", style: "normal"},
        {path: "./fonts/neon/MonaspaceNeon-Bold.woff2", weight: "700", style: "normal"},
        {path: "./fonts/neon/MonaspaceNeon-ExtraBold.woff2", weight: "800", style: "normal"},
        {path: "./fonts/neon/MonaspaceNeon-Italic.woff2", weight: "400", style: "italic"},
        {path: "./fonts/neon/MonaspaceNeon-BoldItalic.woff2", weight: "700", style: "italic"},
    ],
    display: 'swap',
    variable: '--font-neon'
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
        telephone: true,
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
        images: [
            {
                url: '/images/todo.jpeg',
                width: 1600,
                height: 1066,
                alt: 'Steam5',
            },
        ],
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Steam5',
        description: description,
        images: ['/images/todo.jpeg'],
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
