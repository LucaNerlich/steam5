import type {Metadata} from "next";
import LeaderboardTable from "@/components/LeaderboardTable";
import {Suspense} from "react";
import LeaderboardSection from "@/components/LeaderboardSection";
import {buildBreadcrumbJsonLd} from "@/lib/seo";
import {Routes} from "../../routes";

export default async function LeaderboardPage() {
    const breadcrumbJsonLd = buildBreadcrumbJsonLd([
        {name: "Home", url: Routes.home},
        {name: "Leaderboard", url: Routes.leaderboard},
        {name: "All-time", url: Routes.leaderboard},
    ]);

    return (
        <>
            <script type="application/ld+json" dangerouslySetInnerHTML={{
                __html: JSON.stringify(breadcrumbJsonLd)
            }} />
            <LeaderboardSection active="all"
                                title="All-time"
                                subline="Overall points summed across all days">
                <Suspense fallback={<div style={{height: 320, background: 'var(--color-border)', borderRadius: 8}}/>}>
                    <LeaderboardTable mode="all" refreshMs={10000}/>
                </Suspense>
            </LeaderboardSection>
        </>
    );
}

export const metadata: Metadata = {
    title: 'Leaderboard — All-time',
    description: 'All-time rankings and high scores for Steam Review Guesser. Compete for the top spot.',
    alternates: {
        canonical: '/review-guesser/leaderboard',
    },
    keywords: [
        'Steam',
        'leaderboard',
        'review guessing game',
        'high scores',
        'rankings',
        'all-time',
        'daily challenge',
        'weekly challenge',
        'monthly challenge',
        'compete'
    ],
    openGraph: {
        title: 'Leaderboard — All-time',
        description: 'All-time rankings and high scores for Steam Review Guesser. Compete for the top spot.',
        url: '/review-guesser/leaderboard',
        images: ['/opengraph-image'],
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Leaderboard — All-time',
        description: 'All-time rankings and high scores for Steam Review Guesser. Compete for the top spot.',
        images: ['/opengraph-image'],
    },
};


