import type {Metadata} from "next";
import LeaderboardTable from "@/components/LeaderboardTable";
import {buildBreadcrumbJsonLd} from "@/lib/seo";
import {Routes} from "../../routes";
import {fetchLeaderboardPageData} from "@/lib/leaderboard";

// Matches the 10-minute Caffeine TTL on the backend's leaderboard-static cache
export const revalidate = 600;

export default async function LeaderboardPage() {
    const [leaderboardData, achievementsData] = await fetchLeaderboardPageData(
        "all", "all", 600
    );
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
            <LeaderboardTable
                mode="all"
                refreshMs={120000}
                initialData={leaderboardData as any}
                initialAchievements={achievementsData as any}
            />
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


