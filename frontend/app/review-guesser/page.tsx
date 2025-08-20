import type {Metadata} from "next";
import {redirect} from "next/navigation";

export default function ReviewGuesserEntryPage() {
    redirect('/review-guesser/1');
}

export const metadata: Metadata = {
    title: 'Review Guesser',
    description: 'Play the Steam Review guessing game — 5 daily rounds.',
    openGraph: {
        title: 'Review Guesser',
        description: 'Play the Steam Review guessing game — 5 daily rounds.',
        url: '/review-guesser',
        images: ['/opengraph-image'],
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Review Guesser',
        description: 'Play the Steam Review guessing game — 5 daily rounds.',
        images: ['/opengraph-image'],
    },
};


