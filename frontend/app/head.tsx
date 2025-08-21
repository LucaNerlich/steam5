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
        </>
    );
}


