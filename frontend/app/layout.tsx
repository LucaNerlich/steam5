import type {Metadata} from "next";
import "@/styles/globals.css";
import UmamiAnalytics from "@/components/UmamiAnalytics";


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
    applicationName: 'Melanie Nerlich',
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
        <html lang="en">
        <body>
        <header></header>
        <main>
            {children}
        </main>
        <footer></footer>
        <UmamiAnalytics/>
        </body>
        </html>
    );
}
