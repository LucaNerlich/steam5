import type {Metadata} from "next";
import LeaderboardTable from "@/components/LeaderboardTable";
import {Suspense} from "react";
import LeaderboardSection from "@/components/LeaderboardSection";

export default async function LeaderboardWeeklyPage() {
    return (
        <LeaderboardSection active="weekly"
                            title="Weekly"
                            subline="Last seven days">
            <Suspense fallback={<div style={{height: 320, background: 'var(--color-border)', borderRadius: 8}}/>}>
                <LeaderboardTable mode="weekly-floating" refreshMs={10000}/>
            </Suspense>
        </LeaderboardSection>
    );
}

export const metadata: Metadata = {
    title: 'Leaderboard — Weekly',
    description: 'Weekly total points by player for Steam Review Guesser.',
    alternates: {
        canonical: '/review-guesser/leaderboard/weekly',
    },
    openGraph: {
        title: 'Leaderboard — Weekly',
        description: 'Weekly total points by player for Steam Review Guesser.',
        url: '/review-guesser/leaderboard/weekly',
        images: ['/opengraph-image'],
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Leaderboard — Weekly',
        description: 'Weekly total points by player for Steam Review Guesser.',
        images: ['/opengraph-image'],
    },
};
