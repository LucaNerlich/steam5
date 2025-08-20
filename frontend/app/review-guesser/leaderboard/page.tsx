import Link from "next/link";
import LeaderboardTable from "@/components/LeaderboardTable";

export default async function LeaderboardPage() {
    return (
        <section className="container">
            <h1>Review Guesser — Leaderboard</h1>
            <nav className="leaderboard__toggle" aria-label="Leaderboard view">
                <Link href="/review-guesser/leaderboard" className="btn-ghost is-active">All-time</Link>
                <Link href="/review-guesser/leaderboard/weekly" className="btn-ghost">Weekly</Link>
                <Link href="/review-guesser/leaderboard/today" className="btn-ghost">Today</Link>
            </nav>
            <h2>All-time</h2>
            <p className="text-muted">Overall points summed across all days (sorted highest first)</p>
            <LeaderboardTable mode="all" refreshMs={10000}/>
        </section>
    );
}


