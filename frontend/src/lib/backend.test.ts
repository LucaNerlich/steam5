import {describe, expect, it} from 'vitest';
import {forwardedForHeaderValue} from './backend';

describe('forwardedForHeaderValue', () => {
    it('relays the client IP the trusted edge proxy set', () => {
        expect(forwardedForHeaderValue('203.0.113.7')).toEqual({'x-forwarded-for': '203.0.113.7'});
    });

    it('relays a multi-hop chain unchanged rather than picking one entry', () => {
        expect(forwardedForHeaderValue('203.0.113.7, 70.41.3.18')).toEqual({
            'x-forwarded-for': '203.0.113.7, 70.41.3.18',
        });
    });

    it('relays nothing when the edge did not set the header', () => {
        expect(forwardedForHeaderValue(null)).toEqual({});
        expect(forwardedForHeaderValue(undefined)).toEqual({});
        expect(forwardedForHeaderValue('')).toEqual({});
    });
});
