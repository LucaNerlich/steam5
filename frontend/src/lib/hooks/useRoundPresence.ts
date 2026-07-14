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

function toWsOrigin(origin: string): string {
    if (origin.startsWith("https://")) return "wss://" + origin.slice("https://".length);
    if (origin.startsWith("http://")) return "ws://" + origin.slice("http://".length);
    return origin;
}

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
            pingTimerRef.current = setInterval(() => {
                const ws = socketRef.current;
                if (!ws || ws.readyState !== WebSocket.OPEN) return;
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
            const ticket = isSignedIn ? await fetchTicket(scopeKey) : null;
            if (disposed) return;

            const wsOrigin = toWsOrigin(BACKEND_ORIGIN);
            const url = `${wsOrigin}/ws/presence?scopeKey=${encodeURIComponent(scopeKey)}&ticket=${encodeURIComponent(ticket ?? "")}`;

            let ws: WebSocket;
            try {
                ws = new WebSocket(url);
            } catch (e) {
                console.warn("[useRoundPresence] failed to open socket", e);
                scheduleReconnect();
                return;
            }
            socketRef.current = ws;

            ws.onopen = () => {
                if (disposed) return;
                retryCountRef.current = 0;
                setConnected(true);
                setReconnecting(false);
                startPing();
            };

            ws.onmessage = (ev) => {
                if (disposed) return;
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
                if (disposed) return;
                setConnected(false);
                clearPing();
                socketRef.current = null;
                if (!closedByUserRef.current) scheduleReconnect();
            };
        };

        const scheduleReconnect = () => {
            if (disposed || closedByUserRef.current) return;
            setReconnecting(true);
            const attempt = retryCountRef.current++;
            const delay = Math.min(BASE_DELAY_MS * Math.pow(2, attempt), MAX_DELAY_MS);
            clearReconnect();
            reconnectTimerRef.current = setTimeout(() => {
                reconnectTimerRef.current = null;
                void connect();
            }, delay);
        };

        const handleRecovery = () => {
            if (disposed || closedByUserRef.current) return;
            if (socketRef.current?.readyState === WebSocket.OPEN) return;
            retryCountRef.current = 0;
            clearReconnect();
            void connect();
        };

        const handleOnline = handleRecovery;
        const handleVisibilityChange = () => {
            if (document.visibilityState === "visible") {
                handleRecovery();
            }
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
