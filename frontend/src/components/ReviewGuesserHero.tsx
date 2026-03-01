"use client";

import React, {useEffect} from 'react';
import Image from "next/image";
import {Fancybox} from "@fancyapps/ui";
import "@fancyapps/ui/dist/fancybox/fancybox.css";
import {ReviewGameState, SteamAppDetail} from "@/types/review-game";
import {formatDate, formatPrice} from "@/lib/format";
import "@/styles/components/reviewGuesserHero.css";

const formatEntityList = (names: string[], locale?: string): string => {
    if (names.length === 0) return "";
    if (names.length === 1) return names[0];

    if (typeof Intl !== "undefined" && typeof Intl.ListFormat === "function") {
        try {
            const formatter = new Intl.ListFormat(locale, {style: "long", type: "conjunction"});
            const parts = formatter.formatToParts(names);
            const formattedWithSemicolons = parts
                .map((part) => (part.type === "literal" ? "; " : part.value))
                .join("");

            if (formattedWithSemicolons.trim().length > 0) {
                return formattedWithSemicolons;
            }

            return formatter.format(names);
        } catch {
            // Ignore locale formatting errors and fall back to a safe separator.
        }
    }

    return names.join("; ");
};

interface ReviewGuesserHeroProps {
    today: ReviewGameState;
    pick: SteamAppDetail;
    roundIndex: number;
    locale?: string;
}

export default function ReviewGuesserHero(props: Readonly<ReviewGuesserHeroProps>): React.ReactElement {
    const today = props.today;
    const pick = props.pick;
    const totalRounds = today.picks.length;
    const allShots = (pick.screenshots ?? []);
    const developerNames = formatEntityList(
        (pick.developers ?? []).map((developer) => developer.name).filter(Boolean),
        props.locale
    );
    const publisherNames = formatEntityList(
        (pick.publisher ?? []).map((publisher) => publisher.name).filter(Boolean),
        props.locale
    );

    useEffect(() => {
        // Initialize Fancybox for this component's screenshots
        // @ts-ignore
        Fancybox.bind(`[data-fancybox="screenshots-${pick.appId}"]`, {
            Toolbar: {
                display: {
                    left: ["infobar"],
                    middle: [],
                    right: ["close"]
                }
            },
            Images: {
                protected: true
            }
        });

        return () => {
            Fancybox.unbind(`[data-fancybox="screenshots-${pick.appId}"]`);
            Fancybox.close();
        };
    }, [pick.appId]);

    // Helper to ensure URLs are absolute
    const normalizeUrl = (url: string | null | undefined): string => {
        if (!url) return '';
        if (url.startsWith('http://') || url.startsWith('https://')) return url;
        if (url.startsWith('//')) return `https:${url}`;
        return url;
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
                        📅 {formatDate(pick.releaseDate, props.locale)}
                    </span>
                )}
                {(() => {
                    const price = pick.priceOverview;
                    const isFree = price === null || Boolean(pick.isFree || (price && price.finalAmount === 0));
                    if (isFree) {
                        return (
                            <span title='Free to play' className="meta-item">🆓 Free to play</span>
                        );
                    }
                    if (price) {
                        const amountCents = price.finalAmount;
                        const currencyCode = price.currency || 'USD';
                        const formatted = amountCents !== null
                            ? formatPrice(amountCents, currencyCode, props.locale)
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
