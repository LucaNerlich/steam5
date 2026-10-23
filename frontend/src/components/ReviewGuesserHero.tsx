"use client";

import React, {useEffect} from 'react';
import Image from "next/image";
import {Fancybox} from "@fancyapps/ui";
import "@fancyapps/ui/dist/fancybox/fancybox.css";
import {ReviewGameState, SteamAppDetail} from "@/types/review-game";
import {formatDate, formatPrice} from "@/lib/format";
import {textCaption} from "@/components/game/GameInfoVideos";
import "@/styles/components/reviewGuesserHero.css";

// Names are joined with "; " — the previous Intl.ListFormat dance rewrote every literal part
// to "; " anyway, so it reduced to this (and its locale argument was never passed).
const formatEntityList = (names: string[]): string => names.join("; ");

interface ReviewGuesserHeroProps {
    today: ReviewGameState;
    pick: SteamAppDetail;
    roundIndex: number;
}

/** Text-only Fancybox caption formatter, shared with the video surface. */
export function screenshotCaption(_carousel: unknown, slide: { caption?: string }): HTMLElement {
    return textCaption(slide?.caption);
}

/** Bind options for the screenshot gallery; exported so the caption sink is testable. */
export const SCREENSHOT_FANCYBOX_OPTIONS = {
    Carousel: {
        // Steam names are publisher-supplied text; Fancybox's default caption renderer
        // assigns them with innerHTML (see #323/#428).
        formatCaption: screenshotCaption,
    },
    Toolbar: {
        display: {
            left: ["infobar"],
            middle: [],
            right: ["close"],
        },
    },
    Images: {
        protected: true,
    },
} as const;

export default function ReviewGuesserHero(props: Readonly<ReviewGuesserHeroProps>): React.ReactElement {
    const today = props.today;
    const pick = props.pick;
    const totalRounds = today.picks.length;
    const allShots = (pick.screenshots ?? []);
    const developerNames = formatEntityList(
        (pick.developers ?? []).flatMap((developer) => (developer.name ? [developer.name] : []))
    );
    const publisherNames = formatEntityList(
        (pick.publisher ?? []).flatMap((publisher) => (publisher.name ? [publisher.name] : []))
    );

    useEffect(() => {
        // Initialize Fancybox for this component's screenshots
        // @ts-ignore
        Fancybox.bind(`[data-fancybox="screenshots-${pick.appId}"]`, SCREENSHOT_FANCYBOX_OPTIONS);

        return () => {
            Fancybox.unbind(`[data-fancybox="screenshots-${pick.appId}"]`);
            Fancybox.close();
        };
    }, [pick.appId]);

    // Ensure URLs are absolute http(s); anything else (javascript:, data:) is dropped
    // rather than used as an anchor href.
    const normalizeUrl = (url: string | null | undefined): string => {
        if (!url) return '';
        if (url.startsWith('//')) return `https:${url}`;
        if (url.startsWith('http://') || url.startsWith('https://')) return url;
        return '';
    };

    return (
        <section className='review-guesser-hero'>
            <h1 className="game-title">{pick.name}</h1>
            <p>Round <strong>{props.roundIndex}</strong> of <strong>{totalRounds}</strong>
            </p>
            <p className="meta">
                {developerNames && (
                    <span title='Developer' className="meta-item">
                        🧑‍💻 {developerNames}
                    </span>
                )}
                {publisherNames && (
                    <span title='Publisher' className="meta-item">
                        🌍 {publisherNames}
                    </span>
                )}
                {pick.releaseDate && (
                    <span title='Release date' className="meta-item">
                        📅 {formatDate(pick.releaseDate)}
                    </span>
                )}
                {(() => {
                    const price = pick.priceOverview;
                    // price === null means Steam omitted price_overview (delisted/unpriced), not "free".
                    const isFree = Boolean(pick.free || (price && price.finalAmount === 0));
                    if (isFree) {
                        return (
                            <span title='Free to play' className="meta-item">🆓 Free to play</span>
                        );
                    }
                    if (price) {
                        const amountCents = price.finalAmount;
                        const currencyCode = price.currency || 'USD';
                        const formatted = amountCents !== null
                            ? formatPrice(amountCents, currencyCode)
                            : (price.finalFormatted || null);
                        return formatted ? (
                            <span title='Price' className="meta-item">💲 {formatted}</span>
                        ) : null;
                    }
                    return null;
                })()}
            </p>
            {pick.genres && pick.genres.length > 0 && (
                <ul className="genre-pills" aria-label="Genres">
                    {pick.genres.map((g) => (
                        <li key={g.id} className="genre-pill">{g.description}</li>
                    ))}
                </ul>
            )}
            {allShots.length > 0 && (
                <div className='screenshots'>
                    {allShots.map((s, i) => {
                        const fullUrl = normalizeUrl(s.pathFull || s.pathThumbnail);
                        const thumbUrl = normalizeUrl(s.pathThumbnail || s.pathFull);
                        if (!fullUrl || !thumbUrl) return null;
                        return (
                            <a
                                key={s.id}
                                href={fullUrl}
                                data-fancybox={`screenshots-${pick.appId}`}
                                data-caption={`${pick.name} - Screenshot ${i + 1}`}
                                className={`shot ${i >= 4 ? 'shot--hidden' : ''}`}
                                aria-label={`Open screenshot ${i + 1}`}
                            >
                                {i < 4 && (
                                    <Image
                                        src={thumbUrl}
                                        alt={`${pick.name} screenshot ${i + 1}`}
                                        width={400}
                                        height={225}
                                        fetchPriority='high'
                                        placeholder={s.blurdataThumb ? 'blur' : 'empty'}
                                        blurDataURL={s.blurdataThumb || undefined}
                                    />
                                )}
                            </a>
                        );
                    })}
                </div>
            )}
        </section>
    )
}
