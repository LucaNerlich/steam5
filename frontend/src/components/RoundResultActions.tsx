"use client";

import Link from "next/link";
import {ReactNode} from "react";
import {ArrowLeftIcon, ArrowRightIcon, SteamLogoIcon} from "@phosphor-icons/react/ssr";

export default function RoundResultActions(props: {
    appId: number;
    prevHref: string | null;
    nextHref: string | null;
    children?: ReactNode;
}) {
    return (
        <div className="review-round__actions">
            <div className="review-round__actions-inner">
                <Link
                    href={`https://store.steampowered.com/app/${props.appId}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="btn-ghost"
                    aria-label="Open this game on Steam"
                >
                    Open in Steam <SteamLogoIcon size={28}/>
                </Link>
                {props.prevHref && (
                    <Link href={props.prevHref} className="btn-ghost" aria-label="Go to previous round">
                        <ArrowLeftIcon size={28}/> Last round
                    </Link>
                )}
            </div>
            {props.nextHref && (
                <Link href={props.nextHref} className="btn-cta" aria-label="Go to next round">
                    Next round <ArrowRightIcon size={28}/>
                </Link>
            )}
            {props.children}
        </div>
    );
}


