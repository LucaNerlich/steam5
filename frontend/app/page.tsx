import type {Metadata} from "next";
import Link from "next/link";
import {Routes} from "./routes";

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

export const metadata: Metadata = {
    title: 'Home',
    description: 'Steam5 — Steam Review guessing games.',
    openGraph: {
        title: 'Home',
        description: 'Steam5 — Steam Review guessing games.',
        url: '/',
        images: ['/opengraph-image'],
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Home',
        description: 'Steam5 — Steam Review guessing games.',
        images: ['/opengraph-image'],
    },
};
