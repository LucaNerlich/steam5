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

/**
 * Retrieves a WebSocket ticket for the specified scope.
 *
 * @param scopeKey - The scope identifier used to request the ticket.
 * @returns The ticket string, or `null` if the request fails or the response does not contain a valid ticket.
 */
async function fetchTicket(scopeKey: string): Promise<string | null> {
    try {
        const res = await fetch(
            `/api/ws/ticket?scopeKey=${encodeURIComponent(scopeKey)}`,
            {cache: "no-store", credentials: "include"},
        );
        if (!res.ok) return null;
        const data = await res.json();
        return typeof data?.ticket === "string" ? data.ticket : null;
    } catch {
        return null;
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

    const socketRef = useRef<WebSocket | null>(null);
    const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const pingTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
    const closedByUserRef = useRef(false);
    const retryCountRef = useRef(0);
    const connectionIdRef = useRef(0);

    useEffect(() => {
        closedByUserRef.current = false;
        retryCountRef.current = 0;
        setSnapshot(EMPTY_SNAPSHOT);
        setConnected(false);
        setReconnecting(false);

        if (!scopeKey) {
            return () => {
                // nothing to clean up
            };
        }

        let disposed = false;

        const clearReconnect = () => {
            if (reconnectTimerRef.current) {
                clearTimeout(reconnectTimerRef.current);
                reconnectTimerRef.current = null;
            }
        };

        const clearPing = () => {
            if (pingTimerRef.current) {
                clearInterval(pingTimerRef.current);
                pingTimerRef.current = null;
            }
        };

        const closeSocket = () => {
            clearPing();
            const ws = socketRef.current;
            socketRef.current = null;
            if (ws) {
                try {
                    ws.close();
                } catch {
                    // ignore
                }
            }
        };

        const startPing = () => {
            clearPing();
            // Background / sleeping tabs should not keep the socket alive overnight — without
            // pings the server idle sweep (presence.idle-timeout-seconds, default 90s) reclaims
            // the session. Brief tab switches under that window stay connected.
            if (typeof document !== "undefined" && document.visibilityState === "hidden") {
                return;
            }
            pingTimerRef.current = setInterval(() => {
                const ws = socketRef.current;
                if (!ws || ws.readyState !== WebSocket.OPEN) return;
                if (typeof document !== "undefined" && document.visibilityState === "hidden") {
                    clearPing();
                    return;
                }
                try {
                    ws.send(JSON.stringify({type: "ping"}));
                } catch {
                    // let onclose drive reconnect
                }
            }, CLIENT_PING_INTERVAL_MS);
        };

        const connect = async () => {
            if (disposed) return;
            setReconnecting(retryCountRef.current > 0);
            const connectionId = ++connectionIdRef.current;
            const ticket = isSignedIn ? await fetchTicket(scopeKey) : null;
            if (disposed || connectionIdRef.current !== connectionId) return;

            closeSocket();

            const wsOrigin = toWsOrigin(BACKEND_ORIGIN);
            const url = `${wsOrigin}/ws/presence?scopeKey=${encodeURIComponent(scopeKey)}`;
            // Pass the ticket as a WebSocket subprotocol instead of a URL query
            // parameter so it does not land in access logs or proxy log pipelines.
            const subprotocols = ticket ? [`s5ticket.${ticket}`] : undefined;

            let ws: WebSocket;
            try {
                ws = new WebSocket(url, subprotocols);
            } catch (e) {
                console.warn("[useRoundPresence] failed to open socket", e);
                scheduleReconnect();
                return;
            }
            socketRef.current = ws;

            ws.onopen = () => {
                if (disposed || socketRef.current !== ws) return;
                retryCountRef.current = 0;
                setConnected(true);
                setReconnecting(false);
                startPing();
            };

            ws.onmessage = (ev) => {
                if (disposed || socketRef.current !== ws) return;
                try {
                    const data = JSON.parse(ev.data) as Partial<PresenceSnapshot>;
                    setSnapshot({
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
                if (disposed || socketRef.current !== ws) return;
                setConnected(false);
                clearPing();
                socketRef.current = null;
                if (!closedByUserRef.current) scheduleReconnect();
            };
        };

        const scheduleReconnect = () => {
            if (disposed || closedByUserRef.current) return;
            // Don't burn reconnect attempts while the tab is backgrounded overnight.
            if (typeof document !== "undefined" && document.visibilityState === "hidden") {
                setReconnecting(true);
                return;
            }
            setReconnecting(true);
            const attempt = retryCountRef.current++;
            const delay = Math.min(BASE_DELAY_MS * Math.pow(2, attempt), MAX_DELAY_MS)
                + Math.floor(Math.random() * RECONNECT_JITTER_MS);
            clearReconnect();
            reconnectTimerRef.current = setTimeout(() => {
                reconnectTimerRef.current = null;
                void connect();
            }, delay);
        };

        const handleRecovery = () => {
            if (disposed || closedByUserRef.current) return;
            const readyState = socketRef.current?.readyState;
            if (readyState === WebSocket.OPEN || readyState === WebSocket.CONNECTING) return;
            clearReconnect();
            void connect();
        };

        const handleOnline = handleRecovery;
        const handleVisibilityChange = () => {
            if (document.visibilityState === "hidden") {
                clearPing();
                clearReconnect();
                return;
            }
            // Tab visible again: resume keepalive or reconnect if the idle sweep dropped us.
            const readyState = socketRef.current?.readyState;
            if (readyState === WebSocket.OPEN) {
                startPing();
                return;
            }
            handleRecovery();
        };

        window.addEventListener("online", handleOnline);
        window.addEventListener("focus", handleRecovery);
        document.addEventListener("visibilitychange", handleVisibilityChange);

        void connect();

        return () => {
            disposed = true;
            closedByUserRef.current = true;
            window.removeEventListener("online", handleOnline);
            window.removeEventListener("focus", handleRecovery);
            document.removeEventListener("visibilitychange", handleVisibilityChange);
            clearReconnect();
            closeSocket();
            setConnected(false);
            setReconnecting(false);
        };
    }, [scopeKey, isSignedIn]);

    return {...snapshot, connected, reconnecting};
}
