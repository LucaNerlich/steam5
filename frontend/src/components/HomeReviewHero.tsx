import Link from "next/link";
import Image from "next/image";
import {Routes} from "../../app/routes";
import type {ReviewGameState, SteamAppDetail} from "@/types/review-game";
import "@/styles/components/homeReviewHero.css";

function normalizeUrl(url: string | null | undefined): string {
    if (!url) return '';
    if (url.startsWith('http://') || url.startsWith('https://')) return url;
    if (url.startsWith('//')) return `https:${url}`;
    return url;
}

function truncate(text: string | null | undefined, maxLength: number): string {
    if (!text) return '';
    if (text.length <= maxLength) return text;
    return `${text.slice(0, maxLength - 1).trimEnd()}…`;
}

interface HomeReviewHeroProps {
    today: ReviewGameState;
    pick: SteamAppDetail;
}

export default function HomeReviewHero({today, pick}: Readonly<HomeReviewHeroProps>) {
    const screenshot = pick.screenshots?.[0];
    const imageUrl = normalizeUrl(screenshot?.pathFull || screenshot?.pathThumbnail || pick.headerImage || pick.capsuleImage);
    const totalRounds = today.picks.length;
    const developer = pick.developers?.[0]?.name;

    return (
        <section className="home-review-hero" aria-labelledby="home-review-hero-title">
            <div className="home-review-hero__content">
                <p className="home-review-hero__eyebrow">Today&apos;s featured game</p>
                <h1 id="home-review-hero-title" className="home-review-hero__title">{pick.name}</h1>
                <p className="home-review-hero__subtitle">
                    Round <strong>1</strong> of <strong>{totalRounds}</strong>
                    {developer ? <> · by <strong>{developer}</strong></> : null}
                </p>
                {pick.shortDescription && (
                    <p className="home-review-hero__description">{truncate(pick.shortDescription, 180)}</p>
                )}
                {pick.genres && pick.genres.length > 0 && (
                    <ul className="home-review-hero__genres" aria-label="Genres">
                        {pick.genres.slice(0, 4).map((genre) => (
                            <li key={genre.id} className="home-review-hero__genre">{genre.description}</li>
                        ))}
                    </ul>
                )}
                <div className="home-review-hero__actions">
                    <Link href={Routes.reviewGuesser1} className="btn-cta home-review-hero__cta">
                        Guess now
                    </Link>
                    <Link href={Routes.howToPlay} className="btn-ghost">How it works</Link>
                </div>
            </div>
            {imageUrl && (
                <div className="home-review-hero__media">
                    <Image
                        src={imageUrl}
                        alt={`${pick.name} screenshot`}
                        width={640}
                        height={360}
                        className="home-review-hero__image"
                        priority
                        placeholder={screenshot?.blurdataThumb ? 'blur' : 'empty'}
                        blurDataURL={screenshot?.blurdataThumb || undefined}
                    />
                </div>
            )}
        </section>
    );
}
