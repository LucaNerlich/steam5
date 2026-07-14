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
            description="Each day brings three compact rounds. You see the same kind of Steam details as Review Guesser, but the release year stays hidden until you guess it — or unlock hints."
            bucketExample="Guess any year freely. Wrong guesses unlock hints when you're far off: era → narrow range → store date (5 → 4 → 3 → 2 max points)."
            hintTiers={[
                {
                    label: 'No hints',
                    detail: 'Guess the release year from screenshots, genres, and store details alone.',
                    points: 'Max 5 points if exact',
                },
                {
                    label: 'Hint 1 — Era',
                    detail: 'Unlocks after a far-off wrong guess. Reveals the broad decade.',
                    points: 'Max 4 points if exact',
                },
                {
                    label: 'Hint 2 — Narrow range',
                    detail: 'Unlocks when you are closer but still wrong. Shows a tight year window.',
                    points: 'Max 3 points if exact',
                },
                {
                    label: 'Hint 3 — Store date',
                    detail: 'Unlocks on a near miss. Shows the release date as Steam lists it.',
                    points: 'Max 2 points if exact',
                },
            ]}
        />
    );
}
