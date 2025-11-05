import ThemeToggle from "@/components/ThemeToggle";
import FontToggle from "./FontToggle";
import ResetTodayButton from "./ResetTodayButton";
import AuthLogoutLink from "@/components/AuthLogoutLink";
import "@/styles/components/footer.css";
import FooterNavRow from "@/components/FooterNavRow";
import Link from "next/link";

export default function Footer() {
    return (
        <footer>
            <div className="container">
                <FooterNavRow/>
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
                    <ThemeToggle/>
                    <FontToggle/>
                    <ResetTodayButton/>
                    <AuthLogoutLink/>
                </div>
                <small className="text-muted">
                    Enjoying Steam5? <a href="https://paypal.me/lucanerlich" target="_blank" rel="noopener noreferrer">Donate via PayPal</a> · <a href="https://github.com/sponsors/LucaNerlich" target="_blank" rel="noopener noreferrer">GitHub Sponsors</a>
                </small>
                <small className="text-muted">
                    <Link href="/imprint">Imprint</Link> · <Link href="/privacy">Privacy</Link>
                </small>
            </div>
        </footer>
    );
}


