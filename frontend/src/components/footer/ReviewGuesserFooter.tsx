import Link from "next/link";
import NextChallengeTime from "@/components/NextChallengeTime";
import SeasonCountdown from "@/components/SeasonCountdown";
import {Routes} from "../../../app/routes";
import {version} from "../../../package.json";
import FooterShell from "./FooterShell";
import FooterControls from "./FooterControls";

export default function ReviewGuesserFooter() {
    return (
        <FooterShell>
            <div className="footer__meta">
                <small className="footer__meta-line text-muted">
                    <NextChallengeTime/>
                </small>
                <small className="footer__meta-line text-muted">
                    <SeasonCountdown/>
                </small>
                <small className="footer__meta-line footer__meta-links text-muted">
                    <Link href={Routes.howToPlay}>How to Play</Link>
                    <span>·</span>
                    <Link href={Routes.archive}>Archive</Link>
                    <span>·</span>
                    <Link href={Routes.leaderboardToday}>Leaderboard</Link>
                    <span>·</span>
                    <Link href={Routes.imprint}>Imprint</Link>
                    <span>·</span>
                    <Link href={Routes.privacy}>Privacy</Link>
                </small>
                <small className="footer__meta-line text-muted">
                    v{version}
                </small>
            </div>
            <FooterControls showResetToday/>
        </FooterShell>
    );
}
