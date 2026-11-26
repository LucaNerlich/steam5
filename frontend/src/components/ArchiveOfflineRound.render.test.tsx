// @vitest-environment happy-dom
import {act} from 'react';
import {createRoot} from 'react-dom/client';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import ArchiveOfflineRound from './ArchiveOfflineRound';

beforeEach(() => {
    (globalThis as {IS_REACT_ACT_ENVIRONMENT?: boolean}).IS_REACT_ACT_ENVIRONMENT = true;
    window.localStorage.clear();
});

afterEach(() => {
    vi.restoreAllMocks();
});

describe('ArchiveOfflineRound', () => {
    it('still grades a guess when localStorage rejects the write', async () => {
        vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
            throw new DOMException('quota', 'QuotaExceededError');
        });
        const container = document.createElement('div');
        document.body.appendChild(container);
        const root = createRoot(container);

        await act(async () => root.render(
            <ArchiveOfflineRound
                appId={10}
                buckets={['A', 'B']}
                roundIndex={1}
                totalRounds={5}
                gameDate="2026-01-01"
                offlineAnswer={{actualBucket: 'A', totalReviews: 50}}
            />,
        ));
        const button = [...container.querySelectorAll('button')].find((b) => b.textContent === 'B');
        await act(async () => button?.click());

        expect(container.textContent).not.toContain('Submit Your Guess');
        // The point of the fallback: the guess is actually graded and shown, not just the
        // submit card hidden. `submitted` is also true when only unsavedLabel is set.
        const result = container.querySelector('.review-round__result');
        expect(result).not.toBeNull();
        expect(result?.textContent).toContain('Σ=50');
        act(() => root.unmount());
        container.remove();
    });
});
