"use client";

import React, {useCallback, useEffect, useState} from 'react';
import Image from "next/image";
import {ReviewGameState, SteamAppDetail} from "@/types/review-game";
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
    const shots = (pick.screenshots ?? []).slice(0, 8);

    const [isOpen, setIsOpen] = useState(false);
    const [index, setIndex] = useState(0);

    const openAt = useCallback((i: number) => {
        setIndex(i);
        setIsOpen(true);
    }, []);

    const close = useCallback(() => setIsOpen(false), []);
    const prev = useCallback(() => setIndex((i) => (i - 1 + shots.length) % shots.length), [shots.length]);
    const next = useCallback(() => setIndex((i) => (i + 1) % shots.length), [shots.length]);

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
            <p>Game Date: {today.date}</p>
            <p>Round {props.roundIndex} of {totalRounds}</p>
            <h2>{pick.name}</h2>
            <p>
                <span title='Developer' style={{marginRight: '1em'}}>
                🧑‍💻
                    {pick.developers?.map((d) => {
                        return <span key={d.id}>{d.name}</span>
                    })}
                </span>
                <span title='Publisher'>
                🌍
                    {pick.publisher?.map((p) => {
                        return <span key={p.id}>{p.name}</span>
                    })}
                </span>
            </p>
            {shots.length > 0 && (
                <div className='screenshots'>
                    {shots.slice(0, 4).map((s, i) => (
                        <button className='shot' key={s.id} onClick={() => openAt(i)} aria-label="Open screenshot">
                            <Image src={s.pathThumbnail || s.pathFull} alt={pick.name} width={400} height={225}
                                   style={{width: '100%', height: 'auto'}}/>
                        </button>
                    ))}
                </div>
            )}

            {isOpen && shots[index] && (
                <div className="lightbox-overlay" role="dialog" aria-modal="true" onClick={close}>
                    <div className="lightbox-content" onClick={(e) => e.stopPropagation()}>
                        <button className="lightbox-close" aria-label="Close" onClick={close}>✕</button>
                        <div className="lightbox-img">
                            <Image
                                src={shots[index].pathFull || shots[index].pathThumbnail}
                                alt={pick.name}
                                width={1280}
                                height={720}
                                style={{width: '100%', height: 'auto'}}
                                priority
                            />
                        </div>
                        {shots.length > 1 && (
                            <div className="lightbox-nav">
                                <button onClick={prev} aria-label="Previous">←</button>
                                <span>{index + 1}/{shots.length}</span>
                                <button onClick={next} aria-label="Next">→</button>
                            </div>
                        )}
                    </div>
                </div>
            )}
        </section>
    )
}
