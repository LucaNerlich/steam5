import {afterEach, describe, expect, it, vi} from 'vitest';
import React from 'react';
import ReviewGuesserRoundLayout from './layout';

describe('ReviewGuesserRoundLayout', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    it('keeps the presence provider mounted when today cannot be loaded', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, {status: 503})));

        const element = (await ReviewGuesserRoundLayout({children: React.createElement('span')})) as unknown as React.ReactElement<{scopeKey: string}>;

        // An empty scope does not open a socket; bare children would make the context hook
        // throw in GuessSubmissionCard/OtherPlayersNow.
        expect(element.props.scopeKey).toBe('');
    });
});
