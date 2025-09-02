import type {Metadata} from "next";
import {redirect} from "next/navigation";

export default function ReviewGuesserEntryPage() {
    redirect('/review-guesser/1');
}

export const metadata: Metadata = {
    title: 'Review Guesser',
    description: 'Play the Steam review guessing game — five daily rounds. Guess the review bucket and climb the leaderboard.',
    alternates: {
        canonical: '/review-guesser',
    },
    keywords: [
        'Steam',
        'review guessing game',
        'daily challenge',
        'review buckets',
        'leaderboard'
    ],
    openGraph: {
        title: 'Review Guesser',
        description: 'Play the Steam review guessing game — five daily rounds. Guess the review bucket and climb the leaderboard.',
        url: '/review-guesser',
        images: ['/opengraph-image'],
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Review Guesser',
        description: 'Play the Steam review guessing game — five daily rounds. Guess the review bucket and climb the leaderboard.',
        images: ['/opengraph-image'],
    },
};


