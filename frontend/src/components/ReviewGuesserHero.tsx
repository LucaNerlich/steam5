import React from 'react';
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

    return (
        <section className='review-guesser-hero'>
            <h1>Review Guesser</h1>
            <p>Game date: {today.date}</p>
            <p>Round {props.roundIndex} of {totalRounds}</p>
            <h2>{pick.name} ({pick.appId})</h2>
            {pick.screenshots && pick.screenshots.length > 0 && (
                <div style={{
                    display: 'grid',
                    gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
                    gap: '8px',
                    margin: '12px 0'
                }}>
                    {pick.screenshots.slice(0, 4).map(s => (
                        <Image key={s.id} src={s.pathThumbnail || s.pathFull} alt={pick.name} width={400} height={225}
                               style={{width: '100%', height: 'auto'}}/>
                    ))}
                </div>
            )}
        </section>
    )
}
