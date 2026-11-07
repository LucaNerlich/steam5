import {describe, expect, it} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';
import StreaksCard from './StreaksCard';

describe('StreaksCard', () => {
    it('renders server-computed streaks instead of the truncated visible window', () => {
        const html = renderToStaticMarkup(
            <StreaksCard
                rounds={[{date: '2099-01-01'}]}
                todayUtc="2099-01-01"
                currentStreak={450}
                longestStreak={450}
            />,
        );

        expect(html).toContain('<dd>450</dd>');
        expect(html).not.toContain('<dd>1</dd>');
    });
});
