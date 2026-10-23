import {describe, expect, it, vi} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';
import React from 'react';
import ReviewGuesserHero from './ReviewGuesserHero';

// The SSR test has no Next image loader config; render a plain img for this markup test.
vi.mock('next/image', () => ({default: (props: Record<string, unknown>) => <img {...props} alt={String(props.alt ?? '')}/>}));

const base = {
    appId: 1, type: null, name: 'Game', controllerSupport: null, dlc: null,
    shortDescription: null, detailedDescription: null, aboutTheGame: null,
    headerImage: null, capsuleImage: null, website: null, legalNotice: null,
    developers: [], publishers: [], genres: [], categories: [], screenshots: [],
    movies: [], releaseDate: null, metacritic: null, platforms: null,
    priceOverview: null,
} as unknown as Parameters<typeof ReviewGuesserHero>[0]['pick'];

describe('ReviewGuesserHero screenshots', () => {
    const withScreenshots = (pathFull: string, pathThumbnail: string) => ({
        ...base,
        screenshots: [{id: 1, pathFull, pathThumbnail, blurdataThumb: null}],
    }) as never;
    const today = {date: '2026-10-21', picks: []} as never;

    it('drops a non-http screenshot URL instead of using it as an href', () => {
        const html = renderToStaticMarkup(
            <ReviewGuesserHero pick={withScreenshots('javascript:alert(1)', 'javascript:alert(1)')} today={today}/>,
        );
        expect(html).not.toContain('javascript:');
        expect(html).not.toContain('<a');
    });

    it('still renders an https screenshot', () => {
        const html = renderToStaticMarkup(
            <ReviewGuesserHero pick={withScreenshots('https://cdn/full.jpg', 'https://cdn/thumb.jpg')} today={today}/>,
        );
        expect(html).toContain('href="https://cdn/full.jpg"');
    });
});

describe('ReviewGuesserHero price', () => {
    it('labels a genuinely free game as free to play', () => {
        const html = renderToStaticMarkup(<ReviewGuesserHero pick={{...base, free: true} as never} today={{date: '2026-10-21', picks: []} as never}/>);
        expect(html).toContain('Free to play');
    });

    it('does not label a paid app with no price as free', () => {
        // Steam omits price_overview for delisted/unpriced apps; null is unknown, not free.
        const html = renderToStaticMarkup(<ReviewGuesserHero pick={{...base, free: false} as never} today={{date: '2026-10-21', picks: []} as never}/>);
        expect(html).not.toContain('Free to play');
    });
});
