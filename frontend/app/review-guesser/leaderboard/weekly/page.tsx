import type {Metadata} from "next";
import LeaderboardTable from "@/components/LeaderboardTable";
import LeaderboardSection from "@/components/LeaderboardSection";

export default async function LeaderboardWeeklyPage() {
    return (
        <LeaderboardSection active="weekly"
                            title="Weekly"
                            subline="Last seven days total points by player (sorted highest first)">
            <LeaderboardTable mode="weekly-floating" refreshMs={10000}/>
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
