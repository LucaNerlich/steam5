"use client";

import Link from "next/link";
import {ReactNode} from "react";

export default function RoundResultActions(props: {
    appId: number;
    prevHref: string | null;
    nextHref: string | null;
    children?: ReactNode;
}) {
    return (
        <div className="review-round__actions">
            <div className="review-round__actions-inner">
                <a
                    href={`https://store.steampowered.com/app/${props.appId}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="btn-ghost"
                    aria-label="Open this game on Steam"
                >
                    Open Steam ↗
                </a>
                {props.prevHref && (
                    <Link href={props.prevHref} className="btn-ghost" aria-label="Go to previous round">
                        ← Last round
                    </Link>
                )}
            </div>
            {props.nextHref && (
                <Link href={props.nextHref} className="btn-cta" aria-label="Go to next round">Next round →</Link>
            )}
            {props.children}
        </div>
    );
}


