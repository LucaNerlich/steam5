import Link from "next/link";
import "@/styles/components/gameTeaserCard.css";

interface GameTeaserCardProps {
    title: string;
    description: string;
    href: string;
    badge: string;
    icon: string;
    hintPreview: string;
}

export default function GameTeaserCard({
    title,
    description,
    href,
    badge,
    icon,
    hintPreview,
}: Readonly<GameTeaserCardProps>) {
    return (
        <Link href={href} className="game-teaser-card">
            <div className="game-teaser-card__header">
                <span className="game-teaser-card__icon" aria-hidden="true">{icon}</span>
                <span className="game-teaser-card__badge">{badge}</span>
            </div>
            <h2 className="game-teaser-card__title">{title}</h2>
            <p className="game-teaser-card__description">{description}</p>
            <p className="game-teaser-card__hint">{hintPreview}</p>
            <span className="game-teaser-card__cta">Learn more →</span>
        </Link>
    );
}
