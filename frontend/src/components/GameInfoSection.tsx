"use client";

import React from "react";
import type {SteamAppDetail} from "@/types/review-game";
import GameInfoCategories from "@/components/game/GameInfoCategories";
import GameInfoAbout from "@/components/game/GameInfoAbout";
import GameInfoVideos from "@/components/game/GameInfoVideos";
import GameInfoTechnicalInfo from "@/components/game/GameInfoTechnicalInfo";
import "@/styles/components/gameInfoSection.css";

interface Props {
    pick: SteamAppDetail;
}

function uniqueCategoriesFrom(pick?: SteamAppDetail): SteamAppDetail["categories"] {
    const list = Array.isArray(pick?.categories) ? pick!.categories : [];
    const seen = new Set<string>();
    return list.filter((c) => {
        const key = c?.id != null ? String(c.id) : c?.description?.toLowerCase().trim() || "";
        if (!key || seen.has(key)) return false;
        seen.add(key);
        return true;
    });
}

export default function GameInfoSection({pick}: Props): React.ReactElement | null {
    const uniqueCategories = uniqueCategoriesFrom(pick);

    const hasMovies = Array.isArray(pick?.movies) && pick!.movies.length > 0;
    const hasCategories = uniqueCategories.length > 0;
    const hasShort = Boolean(pick?.shortDescription);
    const hasAbout = Boolean(pick?.aboutTheGame);
    const hasTechnical = Boolean(pick && (pick.controllerSupport || pick.windows || pick.mac || pick.linux));
    const hasAny = Boolean(
        pick &&
        (hasMovies || hasCategories || hasShort || hasAbout || hasTechnical)
    );

    if (!pick || !hasAny) return null;

    return (
        <section className="game-info" aria-labelledby="more-about-this-game">
            <h2 id="more-about-this-game" className="game-info__title">More about this game</h2>

            {hasCategories && <GameInfoCategories categories={uniqueCategories}/>}

            {hasShort && (
                <div className="game-info__section">
                    <h3>Short description</h3>
                    <p>{pick.shortDescription}</p>
                </div>
            )}

            {hasAbout && <GameInfoAbout html={pick.aboutTheGame || ""}/>}

            {hasMovies && <GameInfoVideos appId={pick.appId} movies={pick.movies}/>}

            <GameInfoTechnicalInfo
                controllerSupport={pick.controllerSupport}
                windows={pick.windows}
                mac={pick.mac}
                linux={pick.linux}
            />
        </section>
    );
}
