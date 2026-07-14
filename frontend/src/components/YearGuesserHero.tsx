"use client";

import React, {useEffect} from 'react';
import Image from "next/image";
import {Fancybox} from "@fancyapps/ui";
import "@fancyapps/ui/dist/fancybox/fancybox.css";
import type {SteamAppDetail} from "@/types/review-game";
import type {YearGameState} from "@/types/year-game";
import {formatPrice} from "@/lib/format";
import "@/styles/components/yearGuesserHero.css";

const formatEntityList = (names: string[], locale?: string): string => {
    if (names.length === 0) return "";
    if (names.length === 1) return names[0];
    if (typeof Intl !== "undefined" && typeof Intl.ListFormat === "function") {
        try {
            const formatter = new Intl.ListFormat(locale, {style: "long", type: "conjunction"});
            return formatter.format(names).replace(/, /g, "; ");
        } catch {
            // fall through
        }
    }
    return names.join("; ");
};

interface YearGuesserHeroProps {
    today: YearGameState;
    pick: SteamAppDetail;
    roundIndex: number;
    locale?: string;
}

export default function YearGuesserHero(props: Readonly<YearGuesserHeroProps>): React.ReactElement {
    const {today, pick, roundIndex} = props;
    const totalRounds = today.picks.length;
    const allShots = pick.screenshots ?? [];
    const developerNames = formatEntityList(
        (pick.developers ?? []).map((developer) => developer.name).filter(Boolean),
        props.locale,
    );
    const publisherNames = formatEntityList(
        (pick.publisher ?? []).map((publisher) => publisher.name).filter(Boolean),
        props.locale,
    );

    useEffect(() => {
        // @ts-ignore fancybox options typing
        Fancybox.bind(`[data-fancybox="year-screenshots-${pick.appId}"]`, {
            Toolbar: {display: {left: ["infobar"], middle: [], right: ["close"]}},
            Images: {protected: true},
        });
        return () => {
            Fancybox.unbind(`[data-fancybox="year-screenshots-${pick.appId}"]`);
            Fancybox.close();
        };
    }, [pick.appId]);

    const normalizeUrl = (url: string | null | undefined): string => {
        if (!url) return '';
        if (url.startsWith('http://') || url.startsWith('https://')) return url;
        if (url.startsWith('//')) return `https:${url}`;
        return url;
    };

    return (
        <section className="year-guesser-hero">
            <p className="year-guesser-hero__badge">Release Year Guesser</p>
            <h1 className="game-title">{pick.name}</h1>
            <p>Round <strong>{roundIndex}</strong> of <strong>{totalRounds}</strong></p>
            <p className="meta">
                {developerNames && <span className="meta-item">🧑‍💻 {developerNames}</span>}
                {publisherNames && <span className="meta-item">🌍 {publisherNames}</span>}
                {(() => {
                    const price = pick.priceOverview;
                    const isFree = price === null || Boolean(pick.isFree || (price && price.finalAmount === 0));
                    if (isFree) return <span className="meta-item">🆓 Free to play</span>;
                    if (price) {
                        const formatted = price.finalAmount !== null
                            ? formatPrice(price.finalAmount, price.currency || 'USD', props.locale)
                            : price.finalFormatted;
                        return formatted ? <span className="meta-item">💲 {formatted}</span> : null;
                    }
                    return null;
                })()}
            </p>
            {pick.genres && pick.genres.length > 0 && (
                <ul className="genre-pills" aria-label="Genres">
                    {pick.genres.map((genre) => (
                        <li key={genre.id} className="genre-pill">{genre.description}</li>
                    ))}
                </ul>
            )}
            {allShots.length > 0 && (
                <div className="screenshots">
                    {allShots.map((shot, index) => {
                        const fullUrl = normalizeUrl(shot.pathFull || shot.pathThumbnail);
                        const thumbUrl = normalizeUrl(shot.pathThumbnail || shot.pathFull);
                        return (
                            <a
                                key={shot.id}
                                href={fullUrl}
                                data-fancybox={`year-screenshots-${pick.appId}`}
                                data-caption={`${pick.name} - Screenshot ${index + 1}`}
                                className={`shot ${index >= 4 ? 'shot--hidden' : ''}`}
                                aria-label={`Open screenshot ${index + 1}`}
                            >
                                {index < 4 && (
                                    <Image
                                        src={thumbUrl}
                                        alt={`${pick.name} screenshot ${index + 1}`}
                                        width={400}
                                        height={225}
                                        fetchPriority="high"
                                        placeholder={shot.blurdataThumb ? 'blur' : 'empty'}
                                        blurDataURL={shot.blurdataThumb || undefined}
                                    />
                                )}
                            </a>
                        );
                    })}
                </div>
            )}
        </section>
    );
}
