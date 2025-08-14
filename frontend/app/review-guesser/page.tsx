import type {ReviewGameState} from "@/types/review-game";

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

export default async function ReviewGuesserEntryPage() {
    const today = await loadToday();
    return (
        <section className="container">
            <h1>Review Guesser</h1>
            <p>Game date: {today.date}</p>
            <ul>
                {today.picks.map(p => (
                    <li key={p.appId}>{p.name} ({p.appId})</li>
                ))}
            </ul>
        </section>
    );
}


