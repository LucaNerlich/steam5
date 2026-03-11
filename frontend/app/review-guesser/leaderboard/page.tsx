import type {Metadata} from "next";
import LeaderboardTable from "@/components/LeaderboardTable";
import {Suspense} from "react";
import LeaderboardSkeleton from "@/components/LeaderboardSkeleton";
import {buildBreadcrumbJsonLd} from "@/lib/seo";
import {Routes} from "../../routes";
import {fetchLeaderboardPageData} from "@/lib/leaderboard";

async function AllTimeLeaderboardContent() {
    const [leaderboardData, achievementsData] = await fetchLeaderboardPageData(
        "all", "all", 10
    );

    return (
        <LeaderboardTable
            mode="all"
            refreshMs={10000}
            initialData={leaderboardData as any}
            initialAchievements={achievementsData as any}
        />
    );
}

export default function LeaderboardPage() {
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
            <h2>All-time</h2>
            <p className="text-muted">Overall points summed across all days</p>
            <Suspense fallback={<LeaderboardSkeleton variant="table"/>}>
                <AllTimeLeaderboardContent />
            </Suspense>
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


