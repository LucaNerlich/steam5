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
