import type {Metadata} from "next";
import LeaderboardTable from "@/components/LeaderboardTable";
import {Suspense} from "react";
import LeaderboardSection from "@/components/LeaderboardSection";

export default async function LeaderboardMonthlyPage() {
    return (
        <LeaderboardSection active="monthly"
                            title="Monthly"
                            subline="Last thirty days">
            <Suspense fallback={<div style={{height: 320, background: 'var(--color-border)', borderRadius: 8}}/>}>
                <LeaderboardTable mode="monthly" refreshMs={10000}/>
            </Suspense>
        </LeaderboardSection>
    );
}

export const metadata: Metadata = {
    title: 'Leaderboard — Monthly',
    description: 'Monthly total points by player for Steam Review Guesser.',
    alternates: {
        canonical: '/review-guesser/leaderboard/monthly',
    },
    keywords: [
        'Steam',
        'leaderboard',
        'monthly',
        'review guessing game',
        'high scores'
    ],
    openGraph: {
        title: 'Leaderboard — Monthly',
        description: 'Monthly total points by player for Steam Review Guesser.',
        url: '/review-guesser/leaderboard/monthly',
        images: ['/opengraph-image'],
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Leaderboard — Monthly',
        description: 'Monthly total points by player for Steam Review Guesser.',
        images: ['/opengraph-image'],
    },
};

