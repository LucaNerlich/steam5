import Link from "next/link";
import "@/styles/components/gameComingSoon.css";

interface HintTier {
    label: string;
    detail: string;
    points: string;
}

interface GameComingSoonProps {
    title: string;
    tagline: string;
    description: string;
    icon: string;
    hintTiers: HintTier[];
    bucketExample: string;
}

export default function GameComingSoon({
    title,
    tagline,
    description,
    icon,
    hintTiers,
    bucketExample,
}: Readonly<GameComingSoonProps>) {
    return (
        <section className="container game-coming-soon">
            <header className="game-coming-soon__hero">
                <span className="game-coming-soon__icon" aria-hidden="true">{icon}</span>
                <p className="game-coming-soon__badge">Coming soon</p>
                <h1>{title}</h1>
                <p className="game-coming-soon__tagline">{tagline}</p>
                <p className="game-coming-soon__description">{description}</p>
            </header>

            <section className="game-coming-soon__panel" aria-labelledby="hint-system-title">
                <h2 id="hint-system-title">Planned hint system</h2>
                <p className="game-coming-soon__panel-intro">
                    Exact guesses are hard — so each round starts with hidden answers and optional hints.
                    Every hint you reveal lowers the points you can earn.
                </p>
                <ol className="game-coming-soon__hints">
                    {hintTiers.map((tier) => (
                        <li key={tier.label} className="game-coming-soon__hint">
                            <div className="game-coming-soon__hint-label">{tier.label}</div>
                            <p>{tier.detail}</p>
                            <span className="game-coming-soon__hint-points">{tier.points}</span>
                        </li>
                    ))}
                </ol>
                <p className="game-coming-soon__buckets">
                    <strong>Example buckets:</strong> {bucketExample}
                </p>
            </section>

            <p className="game-coming-soon__back">
                <Link href="/">← Back to Steam5 home</Link>
            </p>
        </section>
    );
}
