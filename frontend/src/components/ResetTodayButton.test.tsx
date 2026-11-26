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
});

afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllEnvs();
});

describe('ResetTodayButton', () => {
    it('clears only the UTC day, never the previous day in UTC-negative zones', async () => {
        // In UTC-5 the browser's local date is still yesterday right after UTC midnight, and
        // that key is a real archive day. The old code removed both.
        const utcToday = new Date().toISOString().slice(0, 10);
        const previousUtcDay = new Date(Date.now() - 86_400_000).toISOString().slice(0, 10);
        window.localStorage.setItem(`review-guesser:${utcToday}`, '{}');
        window.localStorage.setItem(`review-guesser:${previousUtcDay}`, '{}');

        const container = document.createElement('div');
        document.body.appendChild(container);
        const root = createRoot(container);
        await act(async () => root.render(<ResetTodayButton/>));
        const button = container.querySelector('button') as HTMLButtonElement;
        await act(async () => button.click());

        expect(window.localStorage.getItem(`review-guesser:${utcToday}`)).toBeNull();
        expect(window.localStorage.getItem(`review-guesser:${previousUtcDay}`)).toBe('{}');
        act(() => root.unmount());
        container.remove();
    });
});
