import Link from "next/link";
import Image from "next/image";
import {Routes} from "../../app/routes";
import "@/styles/components/header.css";
import SteamLoginButton from "@/components/SteamLoginButton";
import IconImage from "../../app/icon.svg"
import IconFullImage from "../../public/icon-full.svg"

export default function Header() {
    return (
        <header>
            <div className="container">
                <Link href={Routes.home} className="brand">
                    <Image
                        src={IconFullImage}
                        alt="Steam5"
                        width={80}
                        height={30}
                        priority
                        className="brand__logo-full"
                    />
                    <Image
                        src={IconImage}
                        alt=""
                        width={20}
                        height={20}
                        priority
                        className="brand__logo-icon"
                    />
                </Link>
                <span className="game-title" aria-hidden="true">
                        <Link href={Routes.reviewGuesser1}>Review Guesser</Link>
                    </span>
                <Link href="/review-guesser/archive" className="archive-link">
                    Archive
                </Link>
                <div className="header-actions">
                    <Link href="/review-guesser/leaderboard/today" className="btn leaderboard-link"
                          title="Leaderboard"
                          aria-label="Open leaderboard">
                        <span className="mobile__show">Scores</span>
                        <span className="mobile__hide">Leaderboard</span>
                    </Link>
                    <a
                        href="https://paypal.me/lucanerlich"
                        className="btn btn-donate"
                        target="_blank"
                        rel="noopener noreferrer"
                        title="Support via PayPal"
                        aria-label="Support via PayPal"
                    >
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden="true" focusable="false">
                            <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>
                        </svg>
                        <span className="mobile__hide">Donate</span>
                    </a>
                    <SteamLoginButton/>
                </div>
            </div>
        </header>
    );
}


