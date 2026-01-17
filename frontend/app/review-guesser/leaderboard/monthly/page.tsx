import type {Metadata} from "next";
import LeaderboardTable from "@/components/LeaderboardTable";
import {Suspense} from "react";
import LeaderboardSection from "@/components/LeaderboardSection";
import {buildBreadcrumbJsonLd} from "@/lib/seo";
import {Routes} from "../../../routes";

export default async function LeaderboardMonthlyPage() {
    const breadcrumbJsonLd = buildBreadcrumbJsonLd([
        {name: "Home", url: Routes.home},
        {name: "Leaderboard", url: Routes.leaderboard},
        {name: "Monthly", url: Routes.leaderboardMonthly},
    ]);

    return (
        <>
            <script type="application/ld+json" dangerouslySetInnerHTML={{
                __html: JSON.stringify(breadcrumbJsonLd)
            }} />
            <LeaderboardSection active="monthly"
                                title="Monthly"
                                subline="Last thirty days">
                <Suspense fallback={<div style={{height: 320, background: 'var(--color-border)', borderRadius: 8}}/>}>
                    <LeaderboardTable mode="monthly" refreshMs={10000}/>
                </Suspense>
            </LeaderboardSection>
        </>
    );
}

export const metadata: Metadata = {
    title: 'Leaderboard — Monthly',
    description: 'Last thirty days rankings and high scores for Steam Review Guesser. Monthly challenge standings.',
    alternates: {
        canonical: '/review-guesser/leaderboard/monthly',
    },
    keywords: [
        'Steam',
        'leaderboard',
        'monthly',
        'review guessing game',
        'high scores',
        'rankings',
        'daily challenge',
        'weekly challenge',
        'monthly challenge',
        'compete'
    ],
    openGraph: {
        title: 'Leaderboard — Monthly',
        description: 'Last thirty days rankings and high scores for Steam Review Guesser. Monthly challenge standings.',
        url: '/review-guesser/leaderboard/monthly',
        images: ['/opengraph-image'],
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Leaderboard — Monthly',
        description: 'Last thirty days rankings and high scores for Steam Review Guesser. Monthly challenge standings.',
        images: ['/opengraph-image'],
    },
};

