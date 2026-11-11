// @vitest-environment happy-dom
import {act} from 'react';
import {createRoot} from 'react-dom/client';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {useRoundPresence} from './useRoundPresence';

vi.mock('@/contexts/AuthContext', () => ({useAuth: () => ({isSignedIn: false})}));

class FakeSocket {
    static readonly CONNECTING = 0;
    static readonly OPEN = 1;
    static readonly CLOSING = 2;
    static readonly CLOSED = 3;
    readyState = FakeSocket.CONNECTING;
    onopen: (() => void) | null = null;
    onclose: (() => void) | null = null;
    onmessage: ((event: {data: string}) => void) | null = null;
    onerror: ((event: unknown) => void) | null = null;
    close = vi.fn();
}

let status: string | undefined;

function Probe({scopeKey}: {scopeKey: string | null}) {
    status = useRoundPresence(scopeKey).status;
    return null;
}

beforeEach(() => {
    (globalThis as {IS_REACT_ACT_ENVIRONMENT?: boolean}).IS_REACT_ACT_ENVIRONMENT = true;
    vi.stubGlobal('WebSocket', FakeSocket);
    status = undefined;
});

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('useRoundPresence status', () => {
    it('reports connecting while the first connect is in flight', async () => {
        const root = createRoot(document.createElement('div'));
        // act flushes effects and the deferred scope-reset microtask, which must not
        // overwrite the "connecting" reported synchronously by start().
        await act(async () => root.render(<Probe scopeKey="review-guesser:2026-10-16"/>));
        expect(status).toBe('connecting');

        await act(async () => root.render(<Probe scopeKey="review-guesser:2026-10-17"/>));
        expect(status).toBe('connecting');

        await act(async () => root.render(<Probe scopeKey={null}/>));
        expect(status).toBe('idle');
        act(() => root.unmount());
    });
});
