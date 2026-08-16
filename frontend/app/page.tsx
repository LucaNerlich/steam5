import Link from "next/link";
import {Routes} from "./routes";

// This page never renders: next.config.ts permanently redirects '/' to
// '/review-guesser/1'. It exists only to own the route.
export default function Home() {
    return (
        <section>
            <h1>Steam5 <em>work-in-progress</em></h1>

            <section className="container">
                <h1>Games</h1>
                <ul>
                    <li><Link href={Routes.reviewGuesser}>Review Guesser</Link></li>
                </ul>
            </section>
        </section>
    );
}
