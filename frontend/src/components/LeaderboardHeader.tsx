"use client";

import {usePathname} from "next/navigation";

import {LEADERBOARD_ROUTES} from "@/lib/leaderboardRoutes";
import {Routes} from "../../app/routes";

// Falls back to the All-time copy, which is also the /leaderboard entry.
const DEFAULT_COPY = LEADERBOARD_ROUTES.find((route) => route.href === Routes.leaderboard)!;

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
