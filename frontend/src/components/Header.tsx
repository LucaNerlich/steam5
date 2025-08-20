"use client";

import Link from "next/link";
import Image from "next/image";
import {Routes} from "../../app/routes";
import "@/styles/components/header.css";
import SteamLoginButton from "@/components/SteamLoginButton";
import {usePathname} from "next/navigation";

export default function Header() {
    const pathname = usePathname();
    const underReviewGuesser = pathname?.startsWith('/review-guesser');
    return (
        <header>
            <div className="container">
                <Link href={Routes.home} className="brand">
                    <Image src="/logo.svg" alt="Steam5" width={20} height={20} priority/>
                    <span>Steam5</span>
                </Link>
                {underReviewGuesser && (
                    <span className="mobile-page-title" aria-hidden="true">Review Guesser</span>
                )}
                <div className="header-actions">
                    {underReviewGuesser && (
                        <Link href="/review-guesser/leaderboard" className="btn leaderboard-link"
                              title="Leaderboard"
                              aria-label="Open leaderboard">
                            <span className="mobile__show">Scores</span>
                            <span className="mobile__hide">Leaderboard</span>
                        </Link>
                    )}
                    <SteamLoginButton/>
                </div>
            </div>
        </header>
    );
}


