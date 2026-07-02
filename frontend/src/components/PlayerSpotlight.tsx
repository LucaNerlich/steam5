import React from "react";
import Link from "next/link";
import "@/styles/components/playerSpotlight.css";
import {BACKEND_ORIGIN as backend} from "@/lib/backend";

type InsightType = "DAY_STREAK" | "WEEKLY_ACHIEVEMENT" | "HOT_STREAK" | "MILESTONE";

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
    WEEKLY_ACHIEVEMENT: "weekly-achievement",
    HOT_STREAK: "hot-streak",
    MILESTONE: "milestone",
};

const INSIGHT_EMOJI: Record<InsightType, string> = {
    DAY_STREAK: "🔥",
    WEEKLY_ACHIEVEMENT: "🏅",
    HOT_STREAK: "📈",
    MILESTONE: "⭐",
};

async function loadSpotlight(): Promise<SpotlightResponse | null> {
    try {
        const res = await fetch(`${backend}/api/stats/spotlight/today`, {
            headers: {"accept": "application/json"},
            next: {revalidate: 300, tags: ["spotlight-today"]},
        });
        if (!res.ok) return null; // includes 204 No Content — nobody eligible yet
        return await res.json();
    } catch {
        return null;
    }
}

export default async function PlayerSpotlight(): Promise<React.ReactElement | null> {
    const spotlight = await loadSpotlight();
    if (!spotlight) return null;

    const modifier = INSIGHT_MODIFIER[spotlight.insightType];

    return (
        <aside className={`player-spotlight player-spotlight--${modifier}`} aria-label="Player spotlight">
            <p className="player-spotlight__eyebrow">Good vibes</p>
            <p className="player-spotlight__headline">
                <span className="player-spotlight__emoji" aria-hidden="true">{INSIGHT_EMOJI[spotlight.insightType]}</span>
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
