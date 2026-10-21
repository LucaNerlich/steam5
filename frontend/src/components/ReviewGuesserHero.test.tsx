import {describe, expect, it} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';
import React from 'react';
import ReviewGuesserHero from './ReviewGuesserHero';

const base = {
    appId: 1, type: null, name: 'Game', controllerSupport: null, dlc: null,
    shortDescription: null, detailedDescription: null, aboutTheGame: null,
    headerImage: null, capsuleImage: null, website: null, legalNotice: null,
    developers: [], publishers: [], genres: [], categories: [], screenshots: [],
    movies: [], releaseDate: null, metacritic: null, platforms: null,
    priceOverview: null,
} as unknown as Parameters<typeof ReviewGuesserHero>[0]['pick'];

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
