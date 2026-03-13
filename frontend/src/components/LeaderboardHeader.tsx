"use client";

import {usePathname} from "next/navigation";

type HeaderCopy = {
    title: string;
    subline: string;
};

const DEFAULT_COPY: HeaderCopy = {
    title: "All-time",
    subline: "Overall points summed across all days",
};

const COPY_BY_PATH: Record<string, HeaderCopy> = {
    "/review-guesser/leaderboard/today": {
        title: "Today",
        subline: "Today's total points by player",
    },
    "/review-guesser/leaderboard/weekly": {
        title: "Weekly",
        subline: "Last seven days",
    },
    "/review-guesser/leaderboard/season": {
        title: "Season",
        subline: "Current season standings",
    },
    "/review-guesser/leaderboard": DEFAULT_COPY,
};

export default function LeaderboardHeader() {
    const pathname = usePathname();
    const copy = COPY_BY_PATH[pathname] ?? DEFAULT_COPY;

    return (
        <>
            <h2>{copy.title}</h2>
            <p className="text-muted">{copy.subline}</p>
        </>
    );
}
