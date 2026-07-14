import type {Metadata} from "next";
import {redirect} from "next/navigation";

export default function YearGuesserEntryPage() {
    redirect('/year-guesser/1');
}

export const metadata: Metadata = {
    title: 'Release Year Guesser',
    description: 'Guess Steam game release years in one daily round. Wrong guesses unlock tiered hints — each hint lowers your max points.',
    alternates: {canonical: '/year-guesser'},
    openGraph: {
        title: 'Release Year Guesser',
        description: 'Guess Steam game release years in one daily round.',
        url: '/year-guesser',
        images: ['/opengraph-image'],
    },
};
