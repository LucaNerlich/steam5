import {afterEach, describe, expect, it, vi} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';

afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('SeasonCountdown', () => {
    it('renders nothing when the backend is unreachable', async () => {
        // A rejected fetch used to throw out of the root layout, which has no global-error.
        vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('down'); }));
        const SeasonCountdown = (await import('./SeasonCountdown')).default;
        await expect(SeasonCountdown()).resolves.toBeNull();
    });

    it('formats a date-only end date in UTC', async () => {
        // endDate is a LocalDate; in a UTC-negative runtime the old formatter showed Dec 30.
        vi.stubEnv('TZ', 'America/New_York');
        vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
            season: {seasonNumber: 1, endDate: '2026-12-31'},
            daysRemaining: 1,
        }), {status: 200, headers: {'content-type': 'application/json'}})));

        const SeasonCountdown = (await import('./SeasonCountdown')).default;
        const html = renderToStaticMarkup(await SeasonCountdown());

        expect(html).toContain('Dec 31');
        expect(html).not.toContain('Dec 30');
    });
});
