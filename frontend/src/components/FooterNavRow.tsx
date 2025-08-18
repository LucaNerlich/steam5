"use client";

import Link from "next/link";
import {usePathname} from "next/navigation";
import React from "react";

export default function FooterNavRow(): React.ReactElement {
    const pathname = usePathname();
    const navMap: Record<string, Array<{ href: string; label: string }>> = {
        "/review-guesser": [
            {href: "/review-guesser/archive", label: "Archive"},
            {href: "/review-guesser/leaderboard", label: "Leaderboard"},
        ],
        default: [
            {href: "/review-guesser/1", label: "Review Guesser"},
            {href: "/review-guesser/leaderboard", label: "Leaderboard"},
        ],
    };
    const matchKey = Object.keys(navMap).find(k => k !== "default" && (pathname?.startsWith(k))) ?? "default";
    const navItems = navMap[matchKey];

    return (
        <nav aria-label="Site sections" className="footer__nav">
            <ul>
                {navItems.map((item) => (
                    <li key={item.href}><Link href={item.href}>{item.label}</Link></li>
                ))}
            </ul>
        </nav>
    );
}


