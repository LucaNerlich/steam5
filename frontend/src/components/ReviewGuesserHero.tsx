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
            <p>Game Date: {today.date}</p>
            <p>Round {props.roundIndex} of {totalRounds}</p>
            <h2>{pick.name}</h2>
            <p>
                <span title='Developer' style={{marginRight: '1em'}}>
                🧑‍💻
                    {pick.developers?.map((d, i) => {
                        return <span>{d.name}</span>
                    })}
                </span>
                <span title='Publisher'>
                🌍
                    {pick.publisher?.map((p, i) => {
                        return <span>{p.name}</span>
                    })}
                </span>
            </p>
            {pick.screenshots && pick.screenshots.length > 0 && (
                <div className='screenshots'>
                    {pick.screenshots.slice(0, 4).map(s => (
                        <div className='shot' key={s.id}>
                            <Image src={s.pathThumbnail || s.pathFull} alt={pick.name} width={400} height={225}
                                   style={{width: '100%', height: 'auto'}}/>
                        </div>
                    ))}
                </div>
            )}
        </section>
    )
}
