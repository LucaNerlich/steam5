import {describe, expect, it} from 'vitest';
import {buildBreadcrumbJsonLd} from './seo';

describe('buildBreadcrumbJsonLd', () => {
    it('produces a schema.org BreadcrumbList with 1-based positions', () => {
        const ld = buildBreadcrumbJsonLd(
            [
                {name: 'Home', url: '/'},
                {name: 'Leaderboard', url: '/review-guesser/leaderboard'},
            ],
            'https://example.com',
        );
        expect(ld['@context']).toBe('https://schema.org');
        expect(ld['@type']).toBe('BreadcrumbList');
        expect(ld.itemListElement).toEqual([
            {'@type': 'ListItem', position: 1, name: 'Home', item: 'https://example.com/'},
            {'@type': 'ListItem', position: 2, name: 'Leaderboard', item: 'https://example.com/review-guesser/leaderboard'},
        ]);
    });

    it('leaves absolute URLs untouched', () => {
        const ld = buildBreadcrumbJsonLd([{name: 'X', url: 'https://other.com/x'}], 'https://example.com');
        expect(ld.itemListElement[0].item).toBe('https://other.com/x');
    });

    it('joins relative URLs that do not start with a slash', () => {
        const ld = buildBreadcrumbJsonLd([{name: 'X', url: 'page'}], 'https://example.com');
        expect(ld.itemListElement[0].item).toBe('https://example.com/page');
    });
});
