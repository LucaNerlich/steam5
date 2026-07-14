import type {Metadata} from "next";
import GameComingSoon from "@/components/GameComingSoon";

export const metadata: Metadata = {
    title: 'Price Guesser — Coming soon',
    description: 'Guess Steam game price tiers in short daily rounds. Optional hints reveal discount and currency — for fewer points.',
    alternates: {canonical: '/price-guesser'},
};

export default function PriceGuesserPage() {
    return (
        <GameComingSoon
            title="Price Guesser"
            icon="💲"
            tagline="What does this game cost?"
            description="Three quick rounds per day. Pick a price bucket instead of typing an exact amount — we start with USD tiers and may expand later."
            bucketExample="Free · Under $5 · $5–$15 · $15–$30 · $30+"
            hintTiers={[
                {
                    label: 'Hint 1 — Discount status',
                    detail: 'Reveal whether the game is on sale right now.',
                    points: 'Max 4 points if correct',
                },
                {
                    label: 'Hint 2 — Currency',
                    detail: 'Show which currency Steam uses for the listed price.',
                    points: 'Max 3 points if correct',
                },
                {
                    label: 'Hint 3 — Formatted price',
                    detail: 'Show the store price as formatted text — still bucket-based scoring.',
                    points: 'Max 2 points if correct',
                },
            ]}
        />
    );
}
