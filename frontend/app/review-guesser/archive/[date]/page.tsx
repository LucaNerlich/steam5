import type {ReviewGameState} from "@/types/review-game";
import ReviewGuesserHero from "@/components/ReviewGuesserHero";
import Link from "next/link";
import {formatDate} from "@/lib/format";
import "@/styles/components/archive.css";
import GameInfoSection from "@/components/GameInfoSection";

export const dynamic = "force-dynamic";

async function loadArchived(date: string): Promise<ReviewGameState | null> {
    const base = process.env.NEXT_PUBLIC_DOMAIN || 'http://localhost:3000';
    const res = await fetch(`${base}/api/review-game/day/${encodeURIComponent(date)}`, {
        headers: {"accept": "application/json"},
        cache: "no-store",
    });
    if (!res.ok) return null;
    return res.json();
}

export default async function ArchivePage({params}: { params: Promise<{ date: string }> }) {
    const {date} = await params;
    const data = await loadArchived(date);
    if (!data || !data.picks || data.picks.length === 0) {
        return (
            <section className="container">
                <h1>Archive</h1>
                <p>No challenge found for {date}.</p>
                <Link href="/review-guesser/1">Go to today’s game</Link>
            </section>
        );
    }

    return (
        <section className="container">
            <h1>Archive — {formatDate(date)}</h1>
            <nav aria-label="Rounds table of contents" className="archive__toc">
                <ol>
                    {data.picks.map((app, idx) => (
                        <li key={`toc-${idx + 1}`}><a href={`#round-${idx + 1}`}>{app.name}</a></li>
                    ))}
                </ol>
            </nav>
            {data.picks.map((pick, idx) => (
                <div key={pick.appId} id={`round-${idx + 1}`} className="archive__round">
                    <ReviewGuesserHero today={data} pick={pick} roundIndex={idx + 1}/>
                    <GameInfoSection pick={pick}/>
                </div>
            ))}
            <p className="text-muted">Archive is read-only; guessing is disabled.</p>
            <Link href="/review-guesser/1" className="btn-ghost">Back to today’s game</Link>
        </section>
    );
}


