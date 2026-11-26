import {describe, expect, it, vi} from 'vitest';

describe('SeasonCountdown', () => {
    it('renders nothing when the backend is unreachable', async () => {
        // A rejected fetch used to throw out of the root layout, which has no global-error.
        vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('down'); }));
        const SeasonCountdown = (await import('./SeasonCountdown')).default;
        await expect(SeasonCountdown()).resolves.toBeNull();
        vi.unstubAllGlobals();
    });
});
