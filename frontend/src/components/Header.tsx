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
                <div className="header-actions">
                    <Link href="/review-guesser/leaderboard" className="btn leaderboard-link"
                          title="Leaderboard"
                          aria-label="Open leaderboard">
                        <span className="mobile__show">Scores</span>
                        <span className="mobile__hide">Leaderboard</span>
                    </Link>
                    <SteamLoginButton/>
                </div>
            </div>
        </header>
    );
}


