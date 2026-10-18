import {describe, expect, it} from 'vitest';
import {buildBreadcrumbJsonLd, genericCardLines, SITE_BASE} from './seo';

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

describe('genericCardLines', () => {
    it('builds the playable URL from the configured site origin', () => {
        const lines = genericCardLines('https://steam5.org');

        expect(lines[0]).toEqual({label: 'Play', value: 'https://steam5.org/review-guesser'});
    });

    it('never advertises the dead steam5.app host', () => {
        const values = genericCardLines('https://steam5.org').map(line => line.value).join(' ');

        expect(values).not.toContain('steam5.app');
    });

    it('defaults to the site origin without a trailing slash', () => {
        expect(genericCardLines()[0].value).toBe(`${SITE_BASE}/review-guesser`);
        expect(SITE_BASE.endsWith('/')).toBe(false);
    });
});
