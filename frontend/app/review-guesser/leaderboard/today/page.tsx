import type {Metadata} from "next";
import LeaderboardTable from "@/components/LeaderboardTable";
import {buildBreadcrumbJsonLd} from "@/lib/seo";
import {Routes} from "../../../routes";
import {fetchLeaderboardPageData} from "@/lib/leaderboard";

// Matches the 60-second Caffeine TTL on the backend's leaderboard-live cache
export const revalidate = 60;

export default async function LeaderboardTodayPage() {
    const [leaderboardData, achievementsData] = await fetchLeaderboardPageData(
        "today", "daily", 60
    );
    const breadcrumbJsonLd = buildBreadcrumbJsonLd([
        {name: "Home", url: Routes.home},
        {name: "Leaderboard", url: Routes.leaderboard},
        {name: "Today", url: Routes.leaderboardToday},
    ]);

    return (
        <>
            <script type="application/ld+json" dangerouslySetInnerHTML={{
                __html: JSON.stringify(breadcrumbJsonLd)
            }} />
            <LeaderboardTable
                mode="today"
                refreshMs={60000}
                initialData={leaderboardData as any}
                initialAchievements={achievementsData as any}
            />
        </>
    );
}

export const metadata: Metadata = {
    title: 'Leaderboard — Today',
    description: "Today's rankings and high scores for Steam Review Guesser. Daily challenge standings to compete on.",
    alternates: {
        canonical: '/review-guesser/leaderboard/today',
    },
    keywords: [
        'Steam',
        'leaderboard',
        'today',
        'daily challenge',
        'review guessing game',
        'rankings',
        'high scores',
        'weekly challenge',
        'monthly challenge',
        'compete'
    ],
    openGraph: {
        title: 'Leaderboard — Today',
        description: "Today's rankings and high scores for Steam Review Guesser. Daily challenge standings to compete on.",
        url: '/review-guesser/leaderboard/today',
        images: ['/opengraph-image'],
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Leaderboard — Today',
        description: "Today's rankings and high scores for Steam Review Guesser. Daily challenge standings to compete on.",
        images: ['/opengraph-image'],
    },
};
