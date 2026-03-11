import type {Metadata} from "next";
import LeaderboardTable from "@/components/LeaderboardTable";
import {Suspense} from "react";
import LeaderboardSection from "@/components/LeaderboardSection";
import LeaderboardSkeleton from "@/components/LeaderboardSkeleton";
import {buildBreadcrumbJsonLd} from "@/lib/seo";
import {Routes} from "../../../routes";
import {fetchLeaderboardPageData} from "@/lib/leaderboard";

async function SeasonLeaderboardContent() {
    const [leaderboardData, achievementsData] = await fetchLeaderboardPageData(
        "season", "season", 10
    );

    return (
        <LeaderboardTable
            mode="season"
            refreshMs={10000}
            initialData={leaderboardData as any}
            initialAchievements={achievementsData as any}
        />
    );
}

export default function LeaderboardSeasonPage() {
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
                <Suspense fallback={<LeaderboardSkeleton variant="table"/>}>
                    <SeasonLeaderboardContent />
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
