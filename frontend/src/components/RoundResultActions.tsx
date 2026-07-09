"use client";

import Link from "next/link";
import {ReactNode} from "react";
import {ArrowLeftIcon, ArrowRightIcon, ShuffleIcon, SteamLogoIcon} from "@phosphor-icons/react/ssr";

export default function RoundResultActions(props: {
    appId: number;
    prevHref: string | null;
    nextHref: string | null;
    randomArchiveHref?: string | null;
    children?: ReactNode;
}) {
    const hasPrev = Boolean(props.prevHref);

    return (
        <div className="review-round__actions">
            <div className={`review-round__actions-secondary${hasPrev ? '' : ' review-round__actions-secondary--single'}`}>
                <Link
                    href={`https://store.steampowered.com/app/${props.appId}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="btn-ghost"
                    aria-label="Open this game on Steam"
                    data-umami-event="open-on-steam">
                    Open on Steam <SteamLogoIcon size={28}/>
                </Link>
                {props.prevHref && (
                    <Link href={props.prevHref} className="btn-ghost" aria-label="Go to previous round">
                        <ArrowLeftIcon size={28}/> Last round
                    </Link>
                )}
            </div>
            {props.nextHref ? (
                <div className="review-round__actions-primary">
                    <Link href={props.nextHref} className="btn-cta" aria-label="Go to next round">
                        Next round <ArrowRightIcon size={28}/>
                    </Link>
                </div>
            ) : props.randomArchiveHref && (
                <div className="review-round__actions-primary">
                    <Link
                        href={props.randomArchiveHref}
                        className="btn-cta"
                        aria-label="Play a random archived round"
                        data-umami-event="random-archive-round">
                        Random archived round <ShuffleIcon size={28}/>
                    </Link>
                </div>
            )}
            {props.children}
        </div>
    );
}


