import type {Metadata} from "next";
import LeaderboardTable from "@/components/LeaderboardTable";
import {Suspense} from "react";
import LeaderboardSection from "@/components/LeaderboardSection";
import {buildBreadcrumbJsonLd} from "@/lib/seo";
import {Routes} from "../../../routes";

export default async function LeaderboardSeasonPage() {
    const breadcrumbJsonLd = buildBreadcrumbJsonLd([
        {name: "Home", url: Routes.home},
        {name: "Leaderboard", url: Routes.leaderboard},
        {name: "Season", url: Routes.leaderboardSeason},
    ]);

    return (
        <>
            <script type="application/ld+json" dangerouslySetInnerHTML={{
                __html: JSON.stringify(breadcrumbJsonLd)
            }} />
            <LeaderboardSection active="season"
                                title="Season"
                                subline="Current season standings">
                <Suspense fallback={<div style={{height: 320, background: 'var(--color-border)', borderRadius: 8}}/>}>
                    <LeaderboardTable mode="season" refreshMs={10000}/>
                </Suspense>
            </LeaderboardSection>
        </>
    );
}

export const metadata: Metadata = {
    title: 'Leaderboard — Season',
    description: 'Current season rankings and high scores for Steam Review Guesser.',
    alternates: {
        canonical: '/review-guesser/leaderboard/season',
    },
    keywords: [
        'Steam',
        'leaderboard',
        'season',
        'review guessing game',
        'high scores',
        'rankings',
        'daily challenge',
        'weekly challenge',
        'compete'
    ],
    openGraph: {
        title: 'Leaderboard — Season',
        description: 'Current season rankings and high scores for Steam Review Guesser.',
        url: '/review-guesser/leaderboard/season',
        images: ['/opengraph-image'],
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Leaderboard — Season',
        description: 'Current season rankings and high scores for Steam Review Guesser.',
        images: ['/opengraph-image'],
    },
};
