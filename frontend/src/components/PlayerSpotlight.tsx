import React from "react";
import Link from "next/link";
import "@/styles/components/playerSpotlight.css";
import {BACKEND_ORIGIN as backend} from "@/lib/backend";

type InsightType =
    | "DAY_STREAK"
    | "BEST_DAY_EVER"
    | "BEAT_THE_ODDS"
    | "WELCOME_BACK"
    | "MOST_IMPROVED"
    | "WEEKLY_ACHIEVEMENT"
    | "HOT_STREAK"
    | "MILESTONE";

type SpotlightResponse = {
    steamId: string;
    personaName: string;
    avatar: string | null;
    insightType: InsightType;
    headline: string;
    detail: string;
    statLabel: string | null;
    statValue: number | null;
};

const INSIGHT_MODIFIER: Record<InsightType, string> = {
    DAY_STREAK: "day-streak",
    BEST_DAY_EVER: "best-day-ever",
    BEAT_THE_ODDS: "beat-the-odds",
    WELCOME_BACK: "welcome-back",
    MOST_IMPROVED: "most-improved",
    WEEKLY_ACHIEVEMENT: "weekly-achievement",
    HOT_STREAK: "hot-streak",
    MILESTONE: "milestone",
};

const INSIGHT_EMOJI: Record<InsightType, string> = {
    DAY_STREAK: "🔥",
    BEST_DAY_EVER: "🏆",
    BEAT_THE_ODDS: "🎯",
    WELCOME_BACK: "👋",
    MOST_IMPROVED: "📊",
    WEEKLY_ACHIEVEMENT: "🏅",
    HOT_STREAK: "📈",
    MILESTONE: "⭐",
};

async function loadSpotlight(): Promise<SpotlightResponse | null> {
    try {
        const res = await fetch(`${backend}/api/stats/spotlight/today`, {
            headers: {"accept": "application/json"},
            next: {revalidate: 300, tags: ["spotlight-today"]},
            signal: AbortSignal.timeout(3000),
        });
        if (res.status === 204) return null; // nobody eligible today — expected, not an error
        if (!res.ok) {
            console.error(`PlayerSpotlight: unexpected ${res.status} response from spotlight endpoint`);
            return null;
        }
        return await res.json();
    } catch (err) {
        console.error("PlayerSpotlight: failed to load spotlight", err);
        return null;
    }
}

export default async function PlayerSpotlight(): Promise<React.ReactElement | null> {
    const spotlight = await loadSpotlight();
    if (!spotlight) return null;

    // Fall back to the MILESTONE styling/emoji for any insightType the frontend
    // doesn't recognize yet (e.g. a new backend tier deployed before this build).
    const modifier = INSIGHT_MODIFIER[spotlight.insightType] ?? "milestone";
    const emoji = INSIGHT_EMOJI[spotlight.insightType] ?? "⭐";

    return (
        <aside className={`player-spotlight player-spotlight--${modifier}`} aria-label="Player spotlight">
            <p className="player-spotlight__eyebrow">Good vibes</p>
            <p className="player-spotlight__headline">
                <span className="player-spotlight__emoji" aria-hidden="true">{emoji}</span>
                {' '}
                <Link href={`/profile/${encodeURIComponent(spotlight.steamId)}`}>
                    <strong>{spotlight.personaName}</strong>
                </Link>
                {' '}{spotlight.headline}
            </p>
            <p className="player-spotlight__detail">{spotlight.detail}</p>
        </aside>
    );
}
