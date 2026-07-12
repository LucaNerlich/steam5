"use client";

import React from "react";
import {useRoundPresence, type PlayerInfo} from "@/lib/hooks/useRoundPresence";
import "@/styles/components/otherPlayersNow.css";

interface OtherPlayersNowProps {
    scopeKey: string;
}

const MAX_VISIBLE_AVATARS = 8;

function initialsFor(name: string | null): string {
    if (!name) return "?";
    const trimmed = name.trim();
    if (!trimmed) return "?";
    return trimmed.charAt(0).toUpperCase();
}

function PlayerAvatar({player}: {player: PlayerInfo}): React.ReactElement {
    const displayName = player.personaName || "Player";
    if (player.avatar) {
        return (
            <img
                className="other-players__avatar"
                src={player.avatar}
                alt={displayName}
                title={displayName}
                loading="lazy"
                referrerPolicy="no-referrer"
            />
        );
    }
    return (
        <span
            className="other-players__avatar"
            title={displayName}
            aria-label={displayName}
        >
            {initialsFor(player.personaName)}
        </span>
    );
}

export default function OtherPlayersNow(props: Readonly<OtherPlayersNowProps>): React.ReactElement | null {
    const {scopeKey} = props;
    const {totalCount, players, connected} = useRoundPresence(scopeKey);

    if (!connected) return null;
    if (totalCount === 0) return null;

    const visible = players.slice(0, MAX_VISIBLE_AVATARS);
    const overflow = Math.max(0, players.length - visible.length);
    const label = totalCount === 1 ? "1 playing now" : `${totalCount} playing now`;

    return (
        <div className="other-players" aria-live="polite">
            {visible.length > 0 && (
                <div className="other-players__avatars" aria-hidden={false}>
                    {visible.map((player) => (
                        <PlayerAvatar key={player.steamId} player={player}/>
                    ))}
                    {overflow > 0 && (
                        <span
                            className="other-players__overflow"
                            title={`${overflow} more player${overflow === 1 ? "" : "s"}`}
                            aria-label={`${overflow} more players`}
                        >
                            +{overflow}
                        </span>
                    )}
                </div>
            )}
            <span className="other-players__count">
                <span className="other-players__dot" aria-hidden="true"/>
                {label}
            </span>
        </div>
    );
}
