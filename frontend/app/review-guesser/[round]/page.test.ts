import {describe, expect, it} from 'vitest';
import {parseRoundIndex} from '@/lib/roundRoute';

describe('parseRoundIndex', () => {
    it('accepts canonical round numbers and rejects everything else', () => {
        // Zero-padded spellings used to render round 1 under a second ISR key instead of 404.
        expect(parseRoundIndex('1')).toBe(1);
        expect(parseRoundIndex('10')).toBe(10);
        expect(parseRoundIndex('01')).toBeNull();
        expect(parseRoundIndex('000001')).toBeNull();
        expect(parseRoundIndex('0')).toBeNull();
        expect(parseRoundIndex('1e2')).toBeNull();
        expect(parseRoundIndex(undefined)).toBeNull();
    });
});
