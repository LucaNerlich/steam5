"use client";

import React from "react";
import {useRoundPresence} from "@/lib/hooks/useRoundPresence";
import "@/styles/components/otherPlayersNow.css";

interface OtherPlayersNowProps {
    scopeKey: string;
}

export default function OtherPlayersNow(props: Readonly<OtherPlayersNowProps>): React.ReactElement | null {
    const {scopeKey} = props;
    const {totalCount, connected} = useRoundPresence(scopeKey);

    if (!connected) return null;
    if (totalCount === 0) return null;

    const label = totalCount === 1 ? "1 playing now" : `${totalCount} playing now`;

    return (
        <div className="other-players" aria-live="polite">
            <span className="other-players__count">
                <span className="other-players__dot" aria-hidden="true"/>
                {label}
            </span>
        </div>
    );
}
