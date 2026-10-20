// @vitest-environment happy-dom
import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {clearAll, loadDay, saveRound} from '@/lib/storage';
import {clearTodayGuestProgress} from './SteamLoginButton';

const round = {appId: 1, selectedLabel: '1-13', actualBucket: '1-13', totalReviews: 10, correct: true};

beforeEach(() => {
    window.localStorage.clear();
});

afterEach(() => {
    clearAll();
});

describe('clearTodayGuestProgress', () => {
    it('keeps archive days and removes only today', () => {
        saveRound('2020-01-01', 1, 5, round);
        saveRound('2026-10-20', 1, 5, round);

        clearTodayGuestProgress(new Date('2026-10-20T12:00:00Z'));

        expect(loadDay('2020-01-01')).not.toBeNull();
        expect(loadDay('2026-10-20')).toBeNull();
    });
});
