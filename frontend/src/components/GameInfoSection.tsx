"use client";

import React from "react";
import type {SteamAppDetail} from "@/types/review-game";
import "@/styles/components/gameInfoSection.css";

interface Props {
    pick: SteamAppDetail;
}

export default function GameInfoSection({pick}: Props): React.ReactElement | null {
    // Hooks must be called unconditionally
    const uniqueCategories = React.useMemo(() => {
        const list = Array.isArray(pick?.categories) ? pick!.categories : [];
        const seen = new Set<string>();
        return list.filter((c) => {
            const key = c?.id != null ? String(c.id) : c?.description?.toLowerCase().trim() || "";
            if (!key || seen.has(key)) return false;
            seen.add(key);
            return true;
        });
    }, [pick]);

    const hasMovies = Array.isArray(pick?.movies) && pick!.movies.length > 0;
    const hasCategories = uniqueCategories.length > 0;
    const hasShort = Boolean(pick?.shortDescription);
    const hasAbout = Boolean(pick?.aboutTheGame);
    const hasController = Boolean(pick?.controllerSupport);
    const hasPlatforms = Boolean(pick?.isWindows || pick?.isMac || pick?.isLinux);
    const hasAny = Boolean(
        pick &&
        (hasMovies || hasCategories || hasShort || hasAbout || hasController || hasPlatforms)
    );

    if (!pick || !hasAny) return null;

    return (
        <section className="game-info" aria-labelledby="game-info-title">
            <h2 id="game-info-title" className="game-info__title">More about this game</h2>

            {hasPlatforms && (
                <div className="game-info__section">
                    <h3>Platforms</h3>
                    <ul className="game-info__badges" aria-label="Supported platforms">
                        {pick.isWindows && <li className="badge">Windows</li>}
                        {pick.isMac && <li className="badge">macOS</li>}
                        {pick.isLinux && <li className="badge">Linux</li>}
                    </ul>
                </div>
            )}

            {hasController && (
                <div className="game-info__section">
                    <h3>Controller</h3>
                    <p className="text-muted">{pick.controllerSupport}</p>
                </div>
            )}

            {hasCategories && (
                <div className="game-info__section">
                    <h3>Categories</h3>
                    <ul className="game-info__pills" aria-label="Categories">
                        {uniqueCategories.map((c) => (
                            <li key={`cat-${c.id ?? c.description?.toLowerCase().trim()}`} className="pill">
                                {c.description}
                            </li>
                        ))}
                    </ul>
                </div>
            )}

            {hasShort && (
                <div className="game-info__section">
                    <h3>Short description</h3>
                    <p>{pick.shortDescription}</p>
                </div>
            )}

            {hasAbout && (
                <div className="game-info__section">
                    <h3>About the game</h3>
                    <div className="game-info__about" dangerouslySetInnerHTML={{__html: pick.aboutTheGame || ""}}/>
                </div>
            )}

            {hasMovies && (
                <div className="game-info__section">
                    <h3>Videos</h3>
                    <div className="game-info__videos">
                        {pick.movies.map((m, i) => (
                            <div key={`mov-${m.id}-${i}`} className="game-info__video">
                                <video controls preload="metadata" poster={m.thumbnail} className="video">
                                    {m.webm && <source src={m.webm} type="video/webm"/>}
                                    {m.mp4 && <source src={m.mp4} type="video/mp4"/>}
                                </video>
                                <div className="caption">{m.name}</div>
                            </div>
                        ))}
                    </div>
                </div>
            )}
        </section>
    );
}
