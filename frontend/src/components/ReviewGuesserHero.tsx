"use client";

import React, {useCallback, useEffect, useState} from 'react';
import Image from "next/image";
import {ReviewGameState, SteamAppDetail} from "@/types/review-game";
import {formatDate, formatPrice} from "@/lib/format";
import "@/styles/components/reviewGuesserHero.css";

interface ReviewGuesserHeroProps {
    today: ReviewGameState;
    pick: SteamAppDetail;
    roundIndex: number;
}

export default function ReviewGuesserHero(props: Readonly<ReviewGuesserHeroProps>): React.ReactElement {
    const today = props.today;
    const pick = props.pick;
    const totalRounds = today.picks.length;
    const allShots = (pick.screenshots ?? []);
    const thumbShots = allShots.slice(0, 4);

    const [isOpen, setIsOpen] = useState(false);
    const [index, setIndex] = useState(0);

    const openAt = useCallback((i: number) => {
        setIndex(i);
        setIsOpen(true);
    }, []);

    const close = useCallback(() => setIsOpen(false), []);
    const prev = useCallback(() => setIndex((i) => (i - 1 + allShots.length) % allShots.length), [allShots.length]);
    const next = useCallback(() => setIndex((i) => (i + 1) % allShots.length), [allShots.length]);

    useEffect(() => {
        if (!isOpen) return;

        function onKey(e: KeyboardEvent) {
            if (e.key === 'Escape') close();
            if (e.key === 'ArrowLeft') prev();
            if (e.key === 'ArrowRight') next();
        }

        window.addEventListener('keydown', onKey);
        return () => window.removeEventListener('keydown', onKey);
    }, [isOpen, close, prev, next]);

    return (
        <section className='review-guesser-hero'>
            <h1>Review Guesser</h1>
            <p>Round <strong>{props.roundIndex}</strong> of <strong>{totalRounds}</strong> | Game Date: {today.date}</p>
            <h2>{pick.name}</h2>
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
                        📅 {formatDate(pick.releaseDate)}
                    </span>
                )}
                {(() => {
                    const price = pick.priceOverview;
                    let priceText: string | null = null;
                    if (pick.isFree || (price && price.finalAmount === 0)) {
                        priceText = 'Free';
                    } else if (price) {
                        const amountCents = typeof price.finalAmount === 'number' ? price.finalAmount : null;
                        const currencyCode = price.currency || 'USD';
                        if (amountCents !== null) {
                            priceText = formatPrice(amountCents, currencyCode);
                        } else if (price.finalFormatted) {
                            priceText = price.finalFormatted; // fallback if amount missing
                        }
                    }
                    return priceText ? (
                        <span title='Price' className="meta-item">💲 {priceText}</span>
                    ) : null;
                })()}
            </p>
            {pick.genres && pick.genres.length > 0 && (
                <ul className="genre-pills" aria-label="Genres">
                    {pick.genres.map((g) => (
                        <li key={g.id} className="genre-pill">{g.description}</li>
                    ))}
                </ul>
            )}
            {thumbShots.length > 0 && (
                <div className='screenshots'>
                    {thumbShots.map((s, i) => (
                        <button className='shot' key={s.id} onClick={() => openAt(i)} aria-label="Open screenshot">
                            <Image src={s.pathThumbnail || s.pathFull} alt={pick.name} width={400} height={225}
                                   style={{width: '100%', height: 'auto'}}/>
                        </button>
                    ))}
                </div>
            )}

            {isOpen && allShots[index] && (
                <div className="lightbox-overlay" role="dialog" aria-modal="true" onClick={close}>
                    <div className="lightbox-content" onClick={(e) => e.stopPropagation()}>
                        <button className="lightbox-close" aria-label="Close" onClick={close}>✕</button>
                        <div className="lightbox-img">
                            <Image
                                src={allShots[index].pathFull || allShots[index].pathThumbnail}
                                alt={pick.name}
                                width={1280}
                                height={720}
                                style={{width: '100%', height: 'auto'}}
                                priority
                            />
                        </div>
                        {allShots.length > 1 && (
                            <div className="lightbox-nav">
                                <button onClick={prev} aria-label="Previous">←</button>
                                <span>{index + 1}/{allShots.length}</span>
                                <button onClick={next} aria-label="Next">→</button>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </section>
    )
}
