import type {Metadata} from "next";
import GameComingSoon from "@/components/GameComingSoon";

export const metadata: Metadata = {
    title: 'Release Year Guesser — Coming soon',
    description: 'Guess Steam game release years in short daily rounds. Tiered hints help you narrow the answer — at a points cost.',
    alternates: {canonical: '/year-guesser'},
};

export default function YearGuesserPage() {
    return (
        <GameComingSoon
            title="Release Year Guesser"
            icon="📅"
            tagline="When did this game come out?"
            description="Each day brings three compact rounds. You see the same kind of Steam details as Review Guesser, but the release year stays hidden until you guess or spend hints."
            bucketExample="e.g. 1990s · 2000–2004 · 2005–2009 · 2010–2014 · 2015+"
            hintTiers={[
                {
                    label: 'Hint 1 — Era',
                    detail: 'Reveal the broad decade or half-decade the game likely belongs to.',
                    points: 'Max 4 points if correct',
                },
                {
                    label: 'Hint 2 — Narrow range',
                    detail: 'Show a tighter year window, such as “between 2016 and 2019”.',
                    points: 'Max 3 points if correct',
                },
                {
                    label: 'Hint 3 — Store date',
                    detail: 'Show the release date exactly as Steam lists it (may still need interpretation).',
                    points: 'Max 2 points if correct',
                },
            ]}
        />
    );
}
