"use client";

import Link from "next/link";
import {usePathname} from "next/navigation";

const ROUTES = [
    {href: "/review-guesser/leaderboard/today", label: "Today"},
    {href: "/review-guesser/leaderboard/weekly", label: "Weekly"},
    {href: "/review-guesser/leaderboard/season", label: "Season"},
    {href: "/review-guesser/leaderboard", label: "All-time"},
];

function isActive(pathname: string, href: string): boolean {
    if (href === "/review-guesser/leaderboard") {
        return pathname === href;
    }
    return pathname.startsWith(href);
}

export default function LeaderboardToggle() {
    const pathname = usePathname();

    return (
        <nav className="leaderboard__toggle" aria-label="Leaderboard view">
            {ROUTES.map((route) => (
                <Link
                    key={route.href}
                    href={route.href}
                    className={`btn-ghost leaderboard__toggle-btn${isActive(pathname, route.href) ? " is-active" : ""}`}
                >
                    {route.label}
                </Link>
            ))}
        </nav>
    );
}
