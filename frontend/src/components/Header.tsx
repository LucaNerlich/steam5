import Link from "next/link";
import Image from "next/image";
import {Routes} from "../../app/routes";
import "@/styles/components/header.css";
import SteamLoginButton from "@/components/SteamLoginButton";
import IconFullImage from "../../public/icon-full.svg"
import {ArchiveIcon, PresentationChartIcon, TipJarIcon, TrophyIcon} from "@phosphor-icons/react/ssr";

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
                        title="Steam5"
                        className="brand__logo-full"
                    />
                </Link>

                <div className="header-actions">
                    <Link href={Routes.leaderboardToday}
                          className="header-actions__link"
                          title="Leaderboard"
                          aria-label="Open leaderboard">
                        <span className="mobile__hide">Leaderboard</span> <PresentationChartIcon size={28}/>
                    </Link>
                    <Link href={Routes.seasons}
                          className="header-actions__link"
                          title="Seasons"
                          aria-label="Open seasons overview">
                        <span className="mobile__hide">Seasons</span> <TrophyIcon size={28}/>
                    </Link>
                    <Link
                        href="https://paypal.me/lucanerlich"
                        className="header__donate-link header-actions__link"
                        target="_blank"
                        rel="noopener noreferrer"
                        title="Support the project on PayPal"
                        aria-label="Donate via PayPal"
                        data-umami-event="donate-paypal-header">
                        <span className="mobile__hide">Donate</span> <TipJarIcon size={28}/>
                    </Link>
                    <SteamLoginButton/>
                </div>
            </div>
        </header>
    );
}


