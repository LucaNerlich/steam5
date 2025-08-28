import type {Metadata} from "next";
import LeaderboardTable from "@/components/LeaderboardTable";
import {Suspense} from "react";
import LeaderboardSection from "@/components/LeaderboardSection";

export default async function LeaderboardTodayPage() {
    return (
        <LeaderboardSection active="today"
                            title="Today"
                            subline={"Today'" + 's total points by player'}>
            <Suspense fallback={<div style={{height: 320, background: 'var(--color-border)', borderRadius: 8}}/>}>
                <LeaderboardTable mode="today" refreshMs={5000}/>
            </Suspense>
        </LeaderboardSection>
    );
}

export const metadata: Metadata = {
    title: 'Leaderboard — Today',
    description: "Today's total points by player for Steam Review Guesser.",
    alternates: {
        canonical: '/review-guesser/leaderboard/today',
    },
    openGraph: {
        title: 'Leaderboard — Today',
        description: "Today's total points by player for Steam Review Guesser.",
        url: '/review-guesser/leaderboard/today',
        images: ['/opengraph-image'],
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Leaderboard — Today',
        description: "Today's total points by player for Steam Review Guesser.",
        images: ['/opengraph-image'],
    },
};
