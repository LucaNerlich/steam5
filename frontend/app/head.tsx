import React from "react";

export default function Head(): React.ReactElement {
    const base = (process.env.NEXT_PUBLIC_DOMAIN || "https://steam5.org").replace(/\/$/, "");
    let origin = base;
    try {
        origin = new URL(base).origin;
    } catch {
        // ignore, fallback to base as-is
    }

    return (
        <>
            {/* Preconnect/DNS-prefetch for Next.js static assets on our origin */}
            <link rel="preconnect" href={origin}/>
            <link rel="dns-prefetch" href={origin}/>
            <script type="application/ld+json" dangerouslySetInnerHTML={{
                __html: JSON.stringify({
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
                    }
                })
            }} />
            <script type="application/ld+json" dangerouslySetInnerHTML={{
                __html: JSON.stringify({
                    '@context': 'https://schema.org',
                    '@type': 'Organization',
                    name: 'Steam5',
                    url: origin,
                    email: 'luca.nerlich@gmail.com'
                })
            }} />
        </>
    );
}


