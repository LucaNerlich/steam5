import {describe, expect, it} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';
import BucketAccuracyBars from './BucketAccuracyBars';
import HitRateVsAverageCard from './HitRateVsAverageCard';
import OutcomeMixBar from './OutcomeMixBar';
import BiasBadge from './BiasBadge';
import RollingHitRateSpark from './RollingHitRateSpark';
import CalendarHeatmap from './CalendarHeatmap';

/**
 * `role="img"` collapses a chart to a single accessible object described only by its label, so a
 * static caption there makes the values unreachable to assistive tech — the per-cell labels and
 * `<title>` elements inside are discarded with the rest of the subtree. These tests pin that the
 * label carries the data.
 *
 * Fixtures are dated in the future so the charts' "last N days" window filter is time-independent.
 * Fixtures dated far in the past make the window empty for the same reason.
 */
const DATED = '2099-01-01';
const STALE = '2000-01-01';

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

    it('HitRateVsAverageCard shows the no-data branch for an empty recent window', () => {
        const html = renderToStaticMarkup(
            <HitRateVsAverageCard rounds={[
                {selectedBucket: '1-100', actualBucket: '1-100', date: STALE},
            ]} globalHitRate={null}/>,
        );

        expect(html).toContain('aria-label="Hit rate vs average: yours no data, global no data"');
        expect(html).toContain('No data');
    });

    it('RollingHitRateSpark shows the no-data branch for an empty recent window', () => {
        const html = renderToStaticMarkup(
            <RollingHitRateSpark rounds={[
                {selectedBucket: '1-100', actualBucket: '1-100', date: STALE},
            ]}/>,
        );

        expect(html).toContain('No data');
        expect(html).not.toContain('latest 0%');
    });

    it('RollingHitRateSpark still names the measured values when the window has rounds', () => {
        const today = new Date().toISOString().slice(0, 10);
        const html = renderToStaticMarkup(
            <RollingHitRateSpark rounds={[
                {selectedBucket: '1-100', actualBucket: '1-100', date: today},
            ]}/>,
        );

        expect(html).toContain('min 100%, max 100%, latest 100%');
        // A one-point run must still paint (a single-point polyline is invisible).
        expect(html).toContain('<circle');
    });

    it('CalendarHeatmap names the best day without an unweighted daily average', () => {
        const today = new Date().toISOString().slice(0, 10);
        const yesterday = new Date(Date.now() - 86_400_000).toISOString().slice(0, 10);
        const html = renderToStaticMarkup(
            <CalendarHeatmap rounds={[
                {date: today, points: 5},
                {date: today, points: 5},
                {date: yesterday, points: 1},
            ]}/>,
        );

        expect(html).toContain('2 active days');
        expect(html).toContain('best day');
        expect(html).not.toContain('busiest');
        expect(html).toContain('2 active days, best day');
        expect(html).not.toContain('active days, average');
    });

    it('CalendarHeatmap pluralises a single active day', () => {
        const today = new Date().toISOString().slice(0, 10);
        const html = renderToStaticMarkup(
            <CalendarHeatmap rounds={[{date: today, points: 5}]}/>,
        );

        expect(html).toContain('1 active day,');
    });

    it('BiasBadge and OutcomeMixBar count the same outcome mix', () => {
        const rounds = [
            {selectedBucket: '', actualBucket: '', date: DATED},
            {selectedBucket: '', actualBucket: '', date: DATED},
            {selectedBucket: '1-100', actualBucket: '1-100', date: DATED},
            {selectedBucket: '101-1000', actualBucket: '1-100', date: DATED},
            {selectedBucket: '1-100', actualBucket: '101-1000', date: DATED},
        ];

        const badge = renderToStaticMarkup(<BiasBadge rounds={rounds}/>);
        const mix = renderToStaticMarkup(<OutcomeMixBar rounds={rounds}/>);

        expect(badge).toContain('33% too high, 33% hits, 33% too low');
        expect(mix).toContain('Outcome mix: Hit 1 (33%), Too High 1 (33%), Too Low 1 (33%)');
    });
});
