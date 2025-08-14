import type {ReviewGameState} from "@/types/review-game";
import ReviewGuesserRound from "../../../src/components/ReviewGuesserRound";
import Image from "next/image";
import Link from "next/link";

async function loadToday(): Promise<ReviewGameState> {
    const base = process.env.NEXT_PUBLIC_DOMAIN || 'http://localhost:3000';
    const res = await fetch(`${base}/api/review-game/today`, {
        headers: {"accept": "application/json"},
        next: {revalidate: 60},
    });
    if (!res.ok) {
        throw new Error(`Failed to load daily picks: ${res.status}`);
    }
    return res.json();
}

export default async function ReviewGuesserRoundPage({params}: { params: Promise<{ round: string }> }) {
    const {round} = await params;
    const roundIndex = Math.max(1, Number.parseInt(round || '1', 10));
    const today = await loadToday();

    const totalRounds = today.picks.length;
    const pick = today.picks[roundIndex - 1];

    if (!pick) {
        return (
            <section className="container">
                <h1>Review Guesser</h1>
                <p>No pick for this round. You may have finished all rounds.</p>
                <Link href="/review-guesser/1">Go to first round</Link>
            </section>
        );
    }

    return (
        <section className="container">
            <h1>Review Guesser</h1>
            <p>Game date: {today.date}</p>
            <p>Round {roundIndex} of {totalRounds}</p>
            <h2>{pick.name} ({pick.appId})</h2>
            {pick.screenshots && pick.screenshots.length > 0 && (
                <div style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
                    gap: '8px',
                    margin: '12px 0'
                }}>
                    {pick.screenshots.slice(0, 4).map(s => (
                        <Image key={s.id} src={s.pathThumbnail || s.pathFull} alt={pick.name} width={400} height={225}
                               style={{width: '100%', height: 'auto'}}/>
                    ))}
                </div>
            )}
            <ReviewGuesserRound
                appId={pick.appId}
                buckets={today.buckets}
                roundIndex={roundIndex}
                totalRounds={totalRounds}
                pickName={pick.name}
                gameDate={today.date}
            />
            <div className="review-rules">
                <h3>How to play</h3>
                <ul>
                    <li>Each day, guess the review-count bucket for each game pick.</li>
                    <li>Tap one bucket to submit your guess. After submission, other choices are locked.</li>
                    <li>Proceed through all rounds for the day, then share your results.</li>
                </ul>
                <h3>Scoring</h3>
                <ul>
                    <li>Points use a linear decay based on distance d between your guess and the actual bucket.</li>
                    <li>Formula: points = max(0, 5 − 2 × d)</li>
                    <li>Emoji: d=0 → 🟩, d=1 → 🟨, d=2 → 🟧, d≥3 → 🟥, invalid → ⬜</li>
                    <li>Max points per round is 5; daily max = 5 × rounds.</li>
                </ul>
            </div>
        </section>
    );
}


