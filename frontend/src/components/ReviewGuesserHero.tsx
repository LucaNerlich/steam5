"use client";

import React, {useEffect} from 'react';
import Image from "next/image";
import {Fancybox} from "@fancyapps/ui";
import "@fancyapps/ui/dist/fancybox/fancybox.css";
import {ReviewGameState, SteamAppDetail} from "@/types/review-game";
import {formatDate, formatPrice} from "@/lib/format";
import "@/styles/components/reviewGuesserHero.css";

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
    const thumbShots = allShots.slice(0, 4);

    useEffect(() => {
        // Initialize Fancybox for this component's screenshots
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

    return (
        <section className='review-guesser-hero'>
            <h1 className="game-title">{pick.name}</h1>
            <p>Round <strong>{props.roundIndex}</strong> of <strong>{totalRounds}</strong> | {formatDate(today.date, props.locale)}
            </p>
            <p className="meta">
                {pick.developers && pick.developers.length > 0 && (
                    <span title='Developer' className="meta-item">
                        🧑‍💻 {pick.developers.map((d) => (
                        <span key={d.id}>{d.name}</span>
                    ))}
                    </span>
                )}
                {pick.publisher && pick.publisher.length > 0 && (
                    <span title='Publisher' className="meta-item">
                        🌍 {pick.publisher.map((p) => (
                        <span key={p.id}>{p.name}</span>
                    ))}
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
                    {allShots.map((s, i) => (
                        <a
                            key={s.id}
                            href={s.pathFull || s.pathThumbnail}
                            data-fancybox={`screenshots-${pick.appId}`}
                            data-caption={`${pick.name} - Screenshot ${i + 1}`}
                            className={`shot ${i >= 4 ? 'shot--hidden' : ''}`}
                            aria-label={`Open screenshot ${i + 1}`}
                        >
                            {i < 4 && (
                                <Image
                                    src={s.pathThumbnail || s.pathFull}
                                    alt={`${pick.name} screenshot ${i + 1}`}
                                    width={400}
                                    height={225}
                                    fetchPriority='high'
                                    placeholder={s.blurdataThumb ? 'blur' : 'empty'}
                                    blurDataURL={s.blurdataThumb || undefined}
                                />
                            )}
                        </a>
                    ))}
                </div>
            )}
        </section>
    )
}
