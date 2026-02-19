import type {Metadata} from "next";
import LeaderboardTable from "@/components/LeaderboardTable";
import {Suspense} from "react";
import LeaderboardSection from "@/components/LeaderboardSection";
import {buildBreadcrumbJsonLd} from "@/lib/seo";
import {Routes} from "../../../routes";
import {fetchLeaderboardPageData} from "@/lib/leaderboard";

export default async function LeaderboardWeeklyPage() {
    const breadcrumbJsonLd = buildBreadcrumbJsonLd([
        {name: "Home", url: Routes.home},
        {name: "Leaderboard", url: Routes.leaderboard},
        {name: "Weekly", url: Routes.leaderboardWeekly},
    ]);

    // Fetch both leaderboard and achievements data in parallel
    const [leaderboardData, achievementsData] = await fetchLeaderboardPageData(
        "weekly?floating=true", "weekly", 10
    );

    return (
        <>
            <script type="application/ld+json" dangerouslySetInnerHTML={{
                __html: JSON.stringify(breadcrumbJsonLd)
            }} />
            <LeaderboardSection active="weekly"
                                title="Weekly"
                                subline="Last seven days">
                <Suspense fallback={<div style={{height: 320, background: 'var(--color-border)', borderRadius: 8}}/>}>
                    <LeaderboardTable
                        mode="weekly-floating"
                        refreshMs={10000}
                        initialData={leaderboardData as any}
                        initialAchievements={achievementsData as any}
                    />
                </Suspense>
            </LeaderboardSection>
        </>
    );
}

export const metadata: Metadata = {
    title: 'Leaderboard — Weekly',
    description: 'Last seven days rankings and high scores for Steam Review Guesser. Weekly challenge standings.',
    alternates: {
        canonical: '/review-guesser/leaderboard/weekly',
    },
    keywords: [
        'Steam',
        'leaderboard',
        'weekly',
        'review guessing game',
        'high scores',
        'rankings',
        'daily challenge',
        'weekly challenge',
        'monthly challenge',
        'compete'
    ],
    openGraph: {
        title: 'Leaderboard — Weekly',
        description: 'Last seven days rankings and high scores for Steam Review Guesser. Weekly challenge standings.',
        url: '/review-guesser/leaderboard/weekly',
        images: ['/opengraph-image'],
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Leaderboard — Weekly',
        description: 'Last seven days rankings and high scores for Steam Review Guesser. Weekly challenge standings.',
        images: ['/opengraph-image'],
    },
};
