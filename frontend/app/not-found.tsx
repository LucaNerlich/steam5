import Link from "next/link";
import "@/styles/components/not-found.css";

export default function NotFound() {
    return (
        <section className="container not-found">
            <div className="not-found__card" role="alert">
                <p className="not-found__eyebrow">404</p>
                <h1 className="not-found__title">Lost in the backlog</h1>
                <p className="not-found__body">
                    Looks like this page hasn’t shipped yet (or was retired). Try heading back to the lobby
                    and keep exploring the Steam5 universe.
                </p>
                <div className="not-found__actions">
                    <Link href="/" className="not-found__button">Return home</Link>
                    <Link href="/review-guesser/seasons" className="not-found__link">
                        Browse seasons →
                    </Link>
                </div>
            </div>
        </section>
    );
}
