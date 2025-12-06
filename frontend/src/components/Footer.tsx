import ThemeToggle from "@/components/ThemeToggle";
import FontToggle from "./FontToggle";
import ResetTodayButton from "./ResetTodayButton";
import AuthLogoutLink from "@/components/AuthLogoutLink";
import "@/styles/components/footer.css";
import FooterNavRow from "@/components/FooterNavRow";
import Link from "next/link";
import NextChallengeTime from "@/components/NextChallengeTime";
import SeasonCountdown from "@/components/SeasonCountdown";

export default function Footer() {
    return (
        <footer>
            <div className="container">
                <FooterNavRow/>
                <div className="footer__meta">
                    <small className="footer__meta-line text-muted">
                        <NextChallengeTime/>
                    </small>
                    <small className="footer__meta-line text-muted">
                        <SeasonCountdown/>
                    </small>
                    <small className="footer__meta-line footer__meta-links text-muted">
                        <Link href="/imprint">Imprint</Link>
                        <span>·</span>
                        <Link href="/privacy">Privacy</Link>
                    </small>
                </div>
                <div className="controls">
                    <a
                        href="https://github.com/LucaNerlich/steam5"
                        className="footer__icon-link"
                        target="_blank"
                        rel="noopener noreferrer"
                        aria-label="Open the Steam5 GitHub repository"
                        title="GitHub"
                    >
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="22" height="22" aria-hidden="true" focusable="false">
                            <path fill="currentColor" d="M12 2C6.48 2 2 6.58 2 12.26c0 4.52 2.87 8.35 6.84 9.71.5.1.68-.22.68-.49 0-.24-.01-.87-.01-1.71-2.78.62-3.37-1.37-3.37-1.37-.46-1.2-1.12-1.52-1.12-1.52-.92-.64.07-.63.07-.63 1.02.07 1.55 1.07 1.55 1.07.9 1.57 2.36 1.12 2.94.86.09-.67.35-1.12.63-1.38-2.22-.26-4.55-1.14-4.55-5.07 0-1.12.39-2.03 1.03-2.75-.1-.26-.45-1.31.1-2.73 0 0 .84-.27 2.75 1.05.8-.23 1.65-.35 2.5-.35s1.7.12 2.5.35c1.9-1.32 2.74-1.05 2.74-1.05.55 1.42.2 2.47.1 2.73.64.72 1.03 1.63 1.03 2.75 0 3.93-2.34 4.8-4.57 5.06.36.32.67.95.67 1.92 0 1.38-.01 2.5-.01 2.85 0 .27.18.6.69.49A10.03 10.03 0 0 0 22 12.26C22 6.58 17.52 2 12 2z"/>
                        </svg>
                    </a>
                    <a
                        href="https://paypal.me/lucanerlich"
                        className="footer__icon-link"
                        target="_blank"
                        rel="noopener noreferrer"
                        aria-label="Support the project on PayPal"
                        title="Donate via PayPal"
                        data-umami-event="donate-paypal-footer"
                    >
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="22" height="22" aria-hidden="true" focusable="false">
                            <path fill="currentColor" d="M7.076 21.337H2.47a.641.641 0 0 1-.633-.74L4.944.901C5.026.382 5.474 0 5.998 0h7.46c2.57 0 4.578.543 5.69 1.81 1.174 1.351.96 3.3.869 4.085v.001c-.092.78-.227 1.92.558 2.85 1.031 1.226 2.66 1.176 3.307 1.176h1.413c.517 0 .96.382 1.044.9l1.274 8.473a.642.642 0 0 1-.633.74h-2.28c-.517 0-.96-.382-1.044-.9l-.6-4.056a.642.642 0 0 0-.633-.74h-2.28c-.517 0-.96-.382-1.044-.9l-.6-4.056a.642.642 0 0 0-.633-.74H9.424c-.517 0-.96.382-1.044.9l-1.272 8.473a.642.642 0 0 1-.633.74z"/>
                        </svg>
                    </a>
                    <a
                        href="https://github.com/sponsors/LucaNerlich"
                        className="footer__icon-link"
                        target="_blank"
                        rel="noopener noreferrer"
                        aria-label="Support the project on GitHub Sponsors"
                        title="Sponsor on GitHub"
                        data-umami-event="donate-github-footer"
                    >
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="22" height="22" aria-hidden="true" focusable="false">
                            <path fill="currentColor" d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z"/>
                        </svg>
                    </a>
                    <ThemeToggle/>
                    <FontToggle/>
                    <ResetTodayButton/>
                    <AuthLogoutLink/>
                </div>
            </div>
        </footer>
    );
}


