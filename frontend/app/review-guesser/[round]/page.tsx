import type {Metadata} from "next";
import type {ReviewGameState} from "@/types/review-game";
import ReviewGuesserClient from "@/components/ReviewGuesserClient";
import {BACKEND_ORIGIN as backend} from "@/lib/backend";

// Round data is fetched on the client (see ReviewGuesserClient). The page itself
// is a thin shell — no server-side fetch of the picks and no caching of the round
// HTML — so it renders instantly and always shows today's fresh round.
export default async function ReviewGuesserRoundPage({params}: { params: Promise<{ round: string }> }) {
    const {round} = await params;
    const roundIndex = Math.max(1, Number.parseInt(round || '1', 10));
    return <ReviewGuesserClient round={roundIndex}/>;
}

export async function generateMetadata({params}: { params: Promise<{ round: string }> }): Promise<Metadata> {
    const {round} = await params;
    try {
        const today: ReviewGameState = await fetch(`${backend}/api/review-game/today`, {
            headers: {"accept": "application/json"},
            next: {revalidate: 600}
        }).then(r => r.json());
        const roundIndex = Math.max(1, Number.parseInt(round || '1', 10));
        const pick = today.picks[roundIndex - 1];
        const title = pick ? `Review Guesser — Round ${roundIndex} of ${today.picks.length}` : `Review Guesser`;
        const description = pick ? `${pick.name} — Can you guess how many Steam reviews it has?` : `Play today's Steam Review guessing game.`;
        const base = (process.env.NEXT_PUBLIC_DOMAIN || 'https://steam5.org').replace(/\/$/, '');
        const firstShot = pick?.screenshots?.[0];
        const rawImg = firstShot?.pathFull || firstShot?.pathThumbnail || '/opengraph-image';
        let imageUrl: string;
        try {
            imageUrl = new URL(rawImg).toString();
        } catch {
            imageUrl = new URL(rawImg.startsWith('/') ? rawImg : `/${rawImg}`, base).toString();
        }
        return {
            title,
            description,
            keywords: [
                'Steam',
                'Steam reviews',
                'review guessing game',
                'daily challenge',
                `round ${roundIndex}`,
                ...(pick?.name ? [pick.name] : [])
            ],
            alternates: {
                canonical: `/review-guesser/${roundIndex}`,
            },
            openGraph: {
                title,
                description,
                url: `/review-guesser/${roundIndex}`,
                images: [imageUrl],
            },
            twitter: {
                card: 'summary_large_image',
                title,
                description,
                images: [imageUrl],
            },
        };
    } catch (error) {
        console.error('Failed to load today', error);

        const title = `Review Guesser`;
        const description = `Play today's Steam Review guessing game.`;
        const ogUrl = new URL((process.env.NEXT_PUBLIC_DOMAIN || 'https://steam5.org').replace(/\/$/, ''));
        ogUrl.pathname = '/opengraph-image';
        return {
            title,
            description,
            keywords: [
                'Steam',
                'Steam reviews',
                'review guessing game',
                'daily challenge'
            ],
            alternates: {
                canonical: `/review-guesser/${round || '1'}`,
            },
            openGraph: {
                title,
                description,
                url: `/review-guesser/${round || '1'}`,
                images: [ogUrl.toString()],
            },
            twitter: {
                card: 'summary_large_image',
                title,
                description,
                images: [ogUrl.toString()],
            },
        };
    }
}


