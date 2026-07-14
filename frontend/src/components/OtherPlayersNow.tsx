"use client";

import React from "react";
import Link from "next/link";
import {useRoundPresenceContext} from "@/contexts/RoundPresenceContext";
import type {PlayerInfo} from "@/lib/hooks/useRoundPresence";
import "@/styles/components/otherPlayersNow.css";

const MAX_VISIBLE_AVATARS = 8;

/**
 * Generates a fallback initial for a player's name.
 *
 * @param name - The player's name, or `null` when unavailable
 * @returns The uppercase first character of the trimmed name, or `?` when no name is provided
 */
function initialsFor(name: string | null): string {
    if (!name) return "?";
    const trimmed = name.trim();
    if (!trimmed) return "?";
    return trimmed.charAt(0).toUpperCase();
}

/**
 * Renders a player's avatar linked to their Steam profile.
 *
 * @param player - The player whose avatar and profile link are rendered.
 * @returns The linked avatar element.
 */
function PlayerAvatar({player}: {player: PlayerInfo}): React.ReactElement {
    const displayName = player.personaName || "Player";
    const profileUrl = `/profile/${player.steamId}`;
    const content = player.avatar ? (
        <img
            className="other-players__avatar"
            src={player.avatar}
            alt={displayName}
            title={displayName}
            width={32}
            height={32}
            loading="lazy"
            referrerPolicy="no-referrer"
        />
    ) : (
        <span
            className="other-players__avatar"
            title={displayName}
            aria-label={displayName}
        >
            {initialsFor(player.personaName)}
        </span>
    );
    return (
        <Link href={profileUrl} aria-label={`View ${displayName}'s Steam profile`}>
            {content}
        </Link>
    );
}

/**
 * Creates the player presence label for the current round.
 *
 * @param uniquePlayerCount - The number of unique players currently present
 * @param reconnecting - Whether the presence connection is reconnecting
 * @returns The appropriate presence status label
 */
function presenceLabel(uniquePlayerCount: number, reconnecting: boolean): string {
    if (reconnecting) return "Reconnecting…";
    if (uniquePlayerCount === 1) return "1 playing now";
    return `${uniquePlayerCount} playing now`;
}

/**
 * Displays the number of players currently active in the round and their avatars when available.
 *
 * @returns The presence indicator, or `null` when the round has no active presence to display.
 */
export default function OtherPlayersNow(): React.ReactElement | null {
    const {uniquePlayerCount, players, connected, reconnecting} = useRoundPresenceContext();

    if (!connected && !reconnecting) return null;
    if (!reconnecting && uniquePlayerCount === 0) return null;

    const visible = players.slice(0, MAX_VISIBLE_AVATARS);
    const overflow = Math.max(0, players.length - visible.length);
    const label = presenceLabel(uniquePlayerCount, reconnecting);

    return (
        <div
            className={`other-players${players.length >= 5 ? " other-players--many" : ""}${reconnecting ? " other-players--reconnecting" : ""}`}
            aria-live="polite"
        >
            {visible.length > 0 && !reconnecting && (
                <div className="other-players__avatars mobile__hide">
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
