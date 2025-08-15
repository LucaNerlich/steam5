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
                    <Link href="/review-guesser/leaderboard" className="btn" aria-label="Open leaderboard">
                        Leaderboard
                    </Link>
                )}
                <SteamLoginButton/>
            </div>
        </header>
    );
}


