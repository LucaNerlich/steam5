import {describe, expect, it} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';
import BucketAccuracyBars from './BucketAccuracyBars';
import OutcomeMixBar from './OutcomeMixBar';

/**
 * `role="img"` collapses a chart to a single accessible object described only by its label, so a
 * static caption there makes the values unreachable to assistive tech — the per-cell labels and
 * `<title>` elements inside are discarded with the rest of the subtree. These tests pin that the
 * label carries the data.
 *
 * Fixtures are dated in the future so the charts' "last N days" window filter is time-independent.
 */
const DATED = '2099-01-01';

describe('performance charts expose their values', () => {
    it('BucketAccuracyBars names every bucket and its hit percentage', () => {
        const html = renderToStaticMarkup(
            <BucketAccuracyBars rounds={[
                {selectedBucket: '1-100', actualBucket: '1-100', date: DATED},
                {selectedBucket: '101-1000', actualBucket: '1-100', date: DATED},
            ]}/>,
        );

        expect(html).toContain('aria-label="Accuracy by bucket: 1-100 50%"');
    });

    it('OutcomeMixBar names every outcome with its count and share', () => {
        const html = renderToStaticMarkup(
            <OutcomeMixBar rounds={[
                {selectedBucket: '1-100', actualBucket: '1-100', date: DATED},
                {selectedBucket: '1-100', actualBucket: '1-100', date: DATED},
                {selectedBucket: '101-1000', actualBucket: '1-100', date: DATED},
                {selectedBucket: '1-100', actualBucket: '101-1000', date: DATED},
            ]}/>,
        );

        expect(html).toContain('aria-label="Outcome mix: Hit 2 (50%), Too High 1 (25%), Too Low 1 (25%)"');
    });
});
