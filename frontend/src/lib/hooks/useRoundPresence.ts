"use client";

import {useEffect, useRef, useState} from "react";
import {useAuth} from "@/contexts/AuthContext";
import {BACKEND_ORIGIN} from "@/lib/backend";

export interface PlayerInfo {
    steamId: string;
    personaName: string | null;
    avatar: string | null;
}

export interface PresenceSnapshot {
    totalCount: number;
    anonymousCount: number;
    uniquePlayerCount: number;
    players: PlayerInfo[];
}

const EMPTY_SNAPSHOT: PresenceSnapshot = {
    totalCount: 0,
    anonymousCount: 0,
    uniquePlayerCount: 0,
    players: [],
};

const BASE_DELAY_MS = 1000;
const MAX_DELAY_MS = 30000;
const CLIENT_PING_INTERVAL_MS = 30000;
/** Random jitter added to reconnect backoff to soften post-restart reconnect herds. */
const RECONNECT_JITTER_MS = 2000;

/**
 * Converts an HTTP origin to its corresponding WebSocket origin.
 *
 * @param origin - The origin to convert
 * @returns The WebSocket-form origin, or `origin` when it uses another scheme
 */
function toWsOrigin(origin: string): string {
    if (origin.startsWith("https://")) return "wss://" + origin.slice("https://".length);
    if (origin.startsWith("http://")) return "ws://" + origin.slice("http://".length);
    return origin;
}

type PresenceCallbacks = {
    onSnapshot: (snapshot: PresenceSnapshot) => void;
    onConnectedChange: (connected: boolean) => void;
    onReconnectingChange: (reconnecting: boolean) => void;
};

/**
 * Owns a single presence socket connection: ticket fetch, WebSocket, keepalive
 * ping, and reconnect backoff. {@link PresenceConnection.destroy} releases every
 * resource the connection allocated, so the effect that started it stays leak-free.
 */
class PresenceConnection {
    private ws: WebSocket | null = null;
    private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    private pingTimer: ReturnType<typeof setInterval> | null = null;
    private closedByUser = false;
    private retryCount = 0;
    private connectionId = 0;
    private disposed = false;
    /** Aborts an in-flight ticket request when the connection is torn down. */
    private ticketController = new AbortController();

    constructor(
        private readonly scopeKey: string,
        private readonly signedIn: boolean,
        private readonly callbacks: PresenceCallbacks,
    ) {}

    /** Opens the first connection. */
    start(): void {
        void this.connect();
    }

    /**
     * Re-connects if the socket dropped (e.g. after laptop wake or network change).
     * No-op while a connection is open or already being established.
     */
    recover(): void {
        if (this.disposed || this.closedByUser) return;
        const readyState = this.ws?.readyState;
        if (readyState === WebSocket.OPEN || readyState === WebSocket.CONNECTING) return;
        this.clearReconnect();
        void this.connect();
    }

    /** Stops keepalive and pending reconnects while the tab is hidden. */
    pause(): void {
        this.clearPing();
        this.clearReconnect();
    }

    /** Resumes keepalive or reconnects once the tab is visible again. */
    resume(): void {
        const readyState = this.ws?.readyState;
        if (readyState === WebSocket.OPEN) {
            this.startPing();
            return;
        }
        this.recover();
    }

    /** Releases every resource: timers, socket, and in-flight ticket request. */
    destroy(): void {
        this.disposed = true;
        this.closedByUser = true;
        this.clearReconnect();
        this.clearPing();
        const ws = this.ws;
        this.ws = null;
        if (ws) {
            try {
                ws.close();
            } catch {
                // ignore
            }
        }
        this.ticketController.abort();
    }

    private clearReconnect(): void {
        if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer);
            this.reconnectTimer = null;
        }
    }

    private clearPing(): void {
        if (this.pingTimer) {
            clearInterval(this.pingTimer);
            this.pingTimer = null;
        }
    }

    private closeSocket(): void {
        this.clearPing();
        const ws = this.ws;
        this.ws = null;
        if (ws) {
            try {
                ws.close();
            } catch {
                // ignore
            }
        }
    }

    private startPing(): void {
        this.clearPing();
        // Background / sleeping tabs should not keep the socket alive overnight — without
        // pings the server idle sweep (presence.idle-timeout-seconds, default 90s) reclaims
        // the session. Brief tab switches under that window stay connected.
        if (typeof document !== "undefined" && document.visibilityState === "hidden") {
            return;
        }
        this.pingTimer = setInterval(() => {
            const ws = this.ws;
            if (!ws || ws.readyState !== WebSocket.OPEN) return;
            if (typeof document !== "undefined" && document.visibilityState === "hidden") {
                this.clearPing();
                return;
            }
            try {
                ws.send(JSON.stringify({type: "ping"}));
            } catch {
                // let onclose drive reconnect
            }
        }, CLIENT_PING_INTERVAL_MS);
    }

    private async connect(): Promise<void> {
        if (this.disposed) return;
        this.callbacks.onReconnectingChange(this.retryCount > 0);
        const connectionId = ++this.connectionId;

        let ticket: string | null = null;
        if (this.signedIn) {
            try {
                // The ticket is passed as a WebSocket subprotocol instead of a URL
                // query parameter so it does not land in access logs or proxy log
                // pipelines.
                const res = await fetch(`/api/ws/ticket?scopeKey=${encodeURIComponent(this.scopeKey)}`, {
                    method: "POST",
                    cache: "no-store",
                    credentials: "include",
                    signal: this.ticketController.signal,
                });
                if (res.ok) {
                    const data = await res.json();
                    ticket = typeof data?.ticket === "string" ? data.ticket : null;
                }
            } catch {
                // Aborted or failed ticket request — connect without a ticket.
            }
        }
        if (this.disposed || this.connectionId !== connectionId) return;

        this.closeSocket();

        const wsOrigin = toWsOrigin(BACKEND_ORIGIN);
        const url = `${wsOrigin}/ws/presence?scopeKey=${encodeURIComponent(this.scopeKey)}`;
        const subprotocols = ticket ? [`s5ticket.${ticket}`] : undefined;

        try {
            this.ws = new WebSocket(url, subprotocols);
        } catch (e) {
            console.warn("[useRoundPresence] failed to open socket", e);
            this.scheduleReconnect();
            return;
        }
        const ws = this.ws;
        if (!ws) return;

        ws.onopen = () => {
            if (this.disposed || this.ws !== ws) return;
            this.retryCount = 0;
            this.callbacks.onConnectedChange(true);
            this.callbacks.onReconnectingChange(false);
            this.startPing();
        };

        ws.onmessage = (ev) => {
            if (this.disposed || this.ws !== ws) return;
            try {
                const data = JSON.parse(ev.data) as Partial<PresenceSnapshot>;
                this.callbacks.onSnapshot({
                    totalCount: typeof data.totalCount === "number" ? data.totalCount : 0,
                    anonymousCount: typeof data.anonymousCount === "number" ? data.anonymousCount : 0,
                    uniquePlayerCount: typeof data.uniquePlayerCount === "number"
                        ? data.uniquePlayerCount
                        : (typeof data.totalCount === "number" ? data.totalCount : 0),
                    players: Array.isArray(data.players) ? data.players : [],
                });
            } catch (e) {
                console.warn("[useRoundPresence] bad message", e);
            }
        };

        ws.onerror = (e) => {
            console.warn("[useRoundPresence] socket error", e);
        };

        ws.onclose = () => {
            if (this.disposed || this.ws !== ws) return;
            this.callbacks.onConnectedChange(false);
            this.clearPing();
            this.ws = null;
            if (!this.closedByUser) this.scheduleReconnect();
        };
    }

    private scheduleReconnect(): void {
        if (this.disposed || this.closedByUser) return;
        // Don't burn reconnect attempts while the tab is backgrounded overnight.
        this.callbacks.onReconnectingChange(true);
        const attempt = this.retryCount++;
        const delay = Math.min(BASE_DELAY_MS * Math.pow(2, attempt), MAX_DELAY_MS)
            + Math.floor(Math.random() * RECONNECT_JITTER_MS);
        this.clearReconnect();
        this.reconnectTimer = setTimeout(() => {
            this.reconnectTimer = null;
            void this.connect();
        }, delay);
    }
}

/**
 * Tracks the real-time presence of players within a round.
 *
 * @param scopeKey - Identifier for the round or presence scope to monitor
 * @returns The current presence snapshot and connection status
 */
export function useRoundPresence(scopeKey: string | null): PresenceSnapshot & {
    connected: boolean;
    reconnecting: boolean;
} {
    const {isSignedIn} = useAuth();
    const [snapshot, setSnapshot] = useState<PresenceSnapshot>(EMPTY_SNAPSHOT);
    const [connected, setConnected] = useState(false);
    const [reconnecting, setReconnecting] = useState(false);

    const connectionRef = useRef<PresenceConnection | null>(null);

    useEffect(() => {
        let disposed = false;

        // Reset presence for the new scope. Deferred to a microtask so the effect
        // doesn't synchronously cascade an extra render — the socket connect below
        // is async anyway, so no message can arrive before this runs.
        queueMicrotask(() => {
            if (disposed) return;
            setSnapshot(EMPTY_SNAPSHOT);
            setConnected(false);
            setReconnecting(false);
        });

        const connection = new PresenceConnection(scopeKey ?? "", Boolean(isSignedIn), {
            onSnapshot: setSnapshot,
            onConnectedChange: setConnected,
            onReconnectingChange: setReconnecting,
        });
        connectionRef.current = connection;

        const handleRecovery = () => {
            if (scopeKey) connection.recover();
        };
        const handleOnline = handleRecovery;
        const handleVisibilityChange = () => {
            if (document.visibilityState === "hidden") {
                connection.pause();
                return;
            }
            // Tab visible again: resume keepalive or reconnect if the idle sweep dropped us.
            if (scopeKey) connection.resume();
        };

        window.addEventListener("online", handleOnline);
        window.addEventListener("focus", handleRecovery);
        document.addEventListener("visibilitychange", handleVisibilityChange);

        if (scopeKey) {
            connection.start();
        }

        return () => {
            disposed = true;
            window.removeEventListener("online", handleOnline);
            window.removeEventListener("focus", handleRecovery);
            document.removeEventListener("visibilitychange", handleVisibilityChange);
            connection.destroy();
            connectionRef.current = null;
            setConnected(false);
            setReconnecting(false);
        };
    }, [scopeKey, isSignedIn]);

    return {...snapshot, connected, reconnecting};
}
