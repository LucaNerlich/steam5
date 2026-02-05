import Link from "next/link";
import {ReactNode} from "react";

export default function LeaderboardSection(props: {
    active: 'all' | 'season' | 'weekly' | 'today';
    title: string;
    subline: string;
    children: ReactNode;
}) {
    return (
        <section className="container">
            <h1>Leaderboard</h1>
            <nav className="leaderboard__toggle" aria-label="Leaderboard view">
                <Link href="/review-guesser/leaderboard/today"
                      className={`btn-ghost${props.active === 'today' ? ' is-active' : ''}`}>Today</Link>
                <Link href="/review-guesser/leaderboard/weekly"
                      className={`btn-ghost${props.active === 'weekly' ? ' is-active' : ''}`}>Weekly</Link>
                <Link href="/review-guesser/leaderboard/season"
                      className={`btn-ghost${props.active === 'season' ? ' is-active' : ''}`}>Season</Link>
                <Link href="/review-guesser/leaderboard"
                      className={`btn-ghost${props.active === 'all' ? ' is-active' : ''}`}>All-time</Link>
            </nav>
            <h2>{props.title}</h2>
            <p className="text-muted">{props.subline}</p>
            {props.children}
        </section>
    );
}


