import type {Metadata} from "next";
import LeaderboardTable from "@/components/LeaderboardTable";
import {Suspense} from "react";
import LeaderboardSection from "@/components/LeaderboardSection";

export default async function LeaderboardPage() {
    return (
        <LeaderboardSection active="all"
                            title="All-time"
                            subline="Overall points summed across all days">
            <Suspense fallback={<div style={{height: 320, background: 'var(--color-border)', borderRadius: 8}}/>}>
                <LeaderboardTable mode="all" refreshMs={10000}/>
            </Suspense>
        </LeaderboardSection>
    );
}

export const metadata: Metadata = {
    title: 'Leaderboard — All-time',
    description: 'Overall points summed across all days for Steam Review Guesser.',
    alternates: {
        canonical: '/review-guesser/leaderboard',
    },
    openGraph: {
        title: 'Leaderboard — All-time',
        description: 'Overall points summed across all days for Steam Review Guesser.',
        url: '/review-guesser/leaderboard',
        images: ['/opengraph-image'],
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Leaderboard — All-time',
        description: 'Overall points summed across all days for Steam Review Guesser.',
        images: ['/opengraph-image'],
    },
};


