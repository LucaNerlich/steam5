import {describe, expect, it} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';
import ShareControls from './ShareControls';
import type {RoundResult} from '@/lib/storage';

const gameWeek = '2026-10-16';
const result: RoundResult = {
    appId: 10,
    pickName: 'Game',
    selectedLabel: 'Positive',
    actualBucket: 'Positive',
    totalReviews: 42,
    correct: true,
};

// Issue #371 acceptance: a render test asserts the share/result markup is
// unchanged. ShareControls must render the bare .share-inline block — the
// former inline branch, which was the branch that actually rendered (the sole
// caller passed `inline`) — without resurrecting the dead branch's
// .review-round__share wrapper, which added a desktop 8px offset and changed
// the mobile flex order.
describe('ShareControls share block markup', () => {
    it('renders div.share-inline with the share button and the Copied span', () => {
        const html = renderToStaticMarkup(
            <ShareControls
                buckets={['Positive', 'Mixed', 'Negative']}
                gameDate={gameWeek}
                totalRounds={1}
                latestRound={1}
                latest={result}
                results={{1: result}}
            />,
        );

        expect(html).toContain('<div class="share-inline">');
        expect(html).toContain('data-umami-event="share-results"');
        expect(html).toContain('Share Results');
        expect(html).toMatch(/share-copied[^>]*>Copied</);
    });

    it('does not render the dead branch\'s .review-round__share wrapper', () => {
        const html = renderToStaticMarkup(
            <ShareControls
                buckets={['Positive', 'Mixed', 'Negative']}
                gameDate={gameWeek}
                totalRounds={1}
                latestRound={1}
                latest={result}
                results={{1: result}}
            />,
        );

        expect(html).not.toContain('review-round__share');
    });
});
