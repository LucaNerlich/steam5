import Link from "next/link";
import {Routes} from "../../../app/routes";
import {version} from "../../../package.json";
import FooterShell from "./FooterShell";
import FooterControls from "./FooterControls";

export default function YearGuesserFooter() {
    return (
        <FooterShell className="footer--game footer--year">
            <div className="footer__meta">
                <small className="footer__meta-line text-muted">Release Year Guesser · coming soon</small>
                <small className="footer__meta-line footer__meta-links text-muted">
                    <Link href={Routes.home}>All games</Link>
                    <span>·</span>
                    <Link href={Routes.reviewGuesser1}>Review Guesser</Link>
                    <span>·</span>
                    <Link href={Routes.priceGuesser}>Price Guesser</Link>
                    <span>·</span>
                    <Link href={Routes.imprint}>Imprint</Link>
                    <span>·</span>
                    <Link href={Routes.privacy}>Privacy</Link>
                </small>
                <small className="footer__meta-line text-muted">v{version}</small>
            </div>
            <FooterControls/>
        </FooterShell>
    );
}
