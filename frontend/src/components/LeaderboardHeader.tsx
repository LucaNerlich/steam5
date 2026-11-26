"use client";

import {usePathname} from "next/navigation";

import {LEADERBOARD_ROUTES} from "@/lib/leaderboardRoutes";

// Falls back to the All-time copy, which is also the /leaderboard entry.
const DEFAULT_COPY = LEADERBOARD_ROUTES.find((route) => route.href === "/review-guesser/leaderboard")!;

export default function LeaderboardHeader() {
    const pathname = usePathname();
    const copy = LEADERBOARD_ROUTES.find((route) => route.href === pathname) ?? DEFAULT_COPY;

    return (
        <>
            <h2>{copy.title}</h2>
            <p className="text-muted">{copy.subline}</p>
        </>
    );
}
