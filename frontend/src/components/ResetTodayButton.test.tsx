// @vitest-environment happy-dom
import {act} from 'react';
import {createRoot} from 'react-dom/client';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

const push = vi.fn();
vi.mock('next/navigation', () => ({useRouter: () => ({push})}));
vi.mock('@/contexts/AuthContext', () => ({useAuthSignedIn: () => false}));

import ResetTodayButton from './ResetTodayButton';

beforeEach(() => {
    (globalThis as {IS_REACT_ACT_ENVIRONMENT?: boolean}).IS_REACT_ACT_ENVIRONMENT = true;
    window.localStorage.clear();
    push.mockClear();
    // 03:00Z is still the previous day in New York, which is the whole point of #693.
    vi.stubEnv('TZ', 'America/New_York');
    vi.useFakeTimers({toFake: ['Date'], now: new Date('2026-11-26T03:00:00Z')});
});

afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
});

describe('ResetTodayButton', () => {
    it('clears only the UTC day, never the previous day in UTC-negative zones', async () => {
        window.localStorage.setItem('review-guesser:2026-11-26', '{}');
        window.localStorage.setItem('review-guesser:2026-11-25', '{}');

        const container = document.createElement('div');
        document.body.appendChild(container);
        const root = createRoot(container);
        await act(async () => root.render(<ResetTodayButton/>));
        const button = container.querySelector('button') as HTMLButtonElement;
        await act(async () => button.click());

        expect(window.localStorage.getItem('review-guesser:2026-11-26')).toBeNull();
        expect(window.localStorage.getItem('review-guesser:2026-11-25')).toBe('{}');
        act(() => root.unmount());
        container.remove();
    });
});
