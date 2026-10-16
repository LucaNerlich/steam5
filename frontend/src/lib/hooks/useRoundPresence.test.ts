import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {PresenceConnection} from './useRoundPresence';

// The presence socket is the only external dependency, so it is faked: every construction is
// recorded, and the test drives onclose to simulate the server's idle sweep closing the socket.
class FakeSocket {
    static instances: FakeSocket[] = [];
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

    constructor(readonly url: string, readonly protocols?: string[]) {
        FakeSocket.instances.push(this);
    }

    /** Simulates `onopen` followed by the server's idle sweep closing the socket. */
    openThenServerClose(): void {
        this.readyState = FakeSocket.OPEN;
        this.onopen?.();
        this.readyState = FakeSocket.CLOSED;
        this.onclose?.();
    }
}

const callbacks = {
    onSnapshot: () => undefined,
    onConnectedChange: () => undefined,
    onReconnectingChange: () => undefined,
};

function startConnection(): PresenceConnection {
    const connection = new PresenceConnection('review-guesser:2026-10-16', false, callbacks);
    connection.start();
    return connection;
}

beforeEach(() => {
    FakeSocket.instances = [];
    vi.useFakeTimers();
    vi.stubGlobal('WebSocket', FakeSocket);
    vi.stubGlobal('document', {visibilityState: 'visible', addEventListener: () => undefined, removeEventListener: () => undefined});
});

afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('PresenceConnection reconnect guard', () => {
    it('does not reconnect after the idle sweep while the document is hidden', () => {
        vi.stubGlobal('document', {visibilityState: 'hidden', addEventListener: () => undefined, removeEventListener: () => undefined});
        const connection = startConnection();
        expect(FakeSocket.instances).toHaveLength(1);

        FakeSocket.instances[0].openThenServerClose();
        vi.advanceTimersByTime(10 * 60_000);

        expect(FakeSocket.instances).toHaveLength(1);
        connection.destroy();
    });

    it('does not reconnect when the tab is hidden before the socket closes', () => {
        const connection = startConnection();
        expect(FakeSocket.instances).toHaveLength(1);
        const socket = FakeSocket.instances[0];

        // The tab is hidden after connect but no pause event reaches the connection.
        vi.stubGlobal('document', {visibilityState: 'hidden', addEventListener: () => undefined, removeEventListener: () => undefined});
        socket.openThenServerClose();
        vi.advanceTimersByTime(10 * 60_000);

        expect(FakeSocket.instances).toHaveLength(1);
        connection.destroy();
    });

    it('reconnects while the document is visible', () => {
        const connection = startConnection();

        FakeSocket.instances[0].openThenServerClose();
        vi.advanceTimersByTime(60_000);

        expect(FakeSocket.instances.length).toBeGreaterThan(1);
        connection.destroy();
    });

    it('reconnects once a hidden tab becomes visible again', () => {
        vi.stubGlobal('document', {visibilityState: 'hidden', addEventListener: () => undefined, removeEventListener: () => undefined});
        const connection = startConnection();
        FakeSocket.instances[0].openThenServerClose();
        vi.advanceTimersByTime(60_000);
        expect(FakeSocket.instances).toHaveLength(1);

        vi.stubGlobal('document', {visibilityState: 'visible', addEventListener: () => undefined, removeEventListener: () => undefined});
        connection.resume();
        vi.advanceTimersByTime(60_000);

        expect(FakeSocket.instances.length).toBeGreaterThan(1);
        connection.destroy();
    });
});
