import type {Metadata} from "next";
import Link from "next/link";
import LeaderboardTable from "@/components/LeaderboardTable";

export default async function LeaderboardTodayPage() {
    return (
        <section className="container">
            <h1>Review Guesser — Leaderboard</h1>
            <nav className="leaderboard__toggle" aria-label="Leaderboard view">
                <Link href="/review-guesser/leaderboard" className="btn-ghost">All-time</Link>
                <Link href="/review-guesser/leaderboard/weekly" className="btn-ghost">Weekly</Link>
                <Link href="/review-guesser/leaderboard/today" className="btn-ghost is-active">Today</Link>
            </nav>
            <h2>Today</h2>
            <p className="text-muted">Today&apos;s total points by player (sorted highest first)</p>
            <LeaderboardTable mode="today" refreshMs={5000}/>
        </section>
    );
}

export const metadata: Metadata = {
    title: 'Leaderboard — Today',
    description: "Today's total points by player for Steam Review Guesser.",
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
