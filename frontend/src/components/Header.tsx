import Link from "next/link";
import Image from "next/image";
import {Routes} from "../../app/routes";
import "@/styles/components/header.css";
import SteamLoginButton from "@/components/SteamLoginButton";
import IconImage from "../../app/icon.svg"
import IconFullImage from "../../public/icon-full.svg"
import {ArchiveIcon, PresentationChartIcon, TipJarIcon} from "@phosphor-icons/react/ssr";

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
                    {/*<Image*/}
                    {/*    src={IconImage}*/}
                    {/*    alt=""*/}
                    {/*    width={20}*/}
                    {/*    height={20}*/}
                    {/*    priority*/}
                    {/*    className="brand__logo-icon"*/}
                    {/*/>*/}
                </Link>
                <span className="game-title" aria-hidden="true">
                    <Link href={Routes.reviewGuesser1} className="mobile__hide">Review Guesser</Link>
                    <Link href="/review-guesser/archive" className="archive-link" title="Archive" aria-label="Open archive">
                    <span className="mobile__hide">Archive</span> <ArchiveIcon size={28}/>
                </Link>
                </span>

                <div className="header-actions">
                    <Link href="/review-guesser/leaderboard/today"
                          style={{display: 'flex', alignItems: 'center', gap: '0.15rem'}}
                          title="Leaderboard"
                          aria-label="Open leaderboard">
                        <span className="mobile__hide">Leaderboard</span> <PresentationChartIcon size={28}/>
                    </Link>
                    <Link
                        href="https://paypal.me/lucanerlich"
                        className="header__donate-link"
                        style={{display: 'flex', alignItems: 'center', gap: '0.15rem'}}
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


