import type {Metadata} from "next";
import Link from "next/link";
import LeaderboardTable from "@/components/LeaderboardTable";

export default async function LeaderboardWeeklyPage() {
    return (
        <section className="container">
            <h1>Review Guesser — Leaderboard</h1>
            <nav className="leaderboard__toggle" aria-label="Leaderboard view">
                <Link href="/review-guesser/leaderboard" className="btn-ghost">All-time</Link>
                <Link href="/review-guesser/leaderboard/weekly" className="btn-ghost is-active">Weekly</Link>
                <Link href="/review-guesser/leaderboard/today" className="btn-ghost">Today</Link>
            </nav>
            <h2>Weekly</h2>
            <p className="text-muted">Last seven days total points by player (sorted highest first)</p>
            <LeaderboardTable mode="weekly-floating" refreshMs={10000}/>
        </section>
    );
}

export const metadata: Metadata = {
    title: 'Leaderboard — Weekly',
    description: 'Weekly total points by player for Steam Review Guesser.',
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
