import type {Metadata} from "next";
import LeaderboardTable from "@/components/LeaderboardTable";
import {Suspense} from "react";
import LeaderboardSection from "@/components/LeaderboardSection";
import {buildBreadcrumbJsonLd} from "@/lib/seo";
import {Routes} from "../../../routes";

export default async function LeaderboardTodayPage() {
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
            <LeaderboardSection active="today"
                                title="Today"
                                subline={"Today'" + 's total points by player'}>
                <Suspense fallback={<div style={{height: 320, background: 'var(--color-border)', borderRadius: 8}}/>}>
                    <LeaderboardTable mode="today" refreshMs={5000}/>
                </Suspense>
            </LeaderboardSection>
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
