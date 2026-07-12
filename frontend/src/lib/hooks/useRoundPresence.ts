"use client";

import {useEffect, useRef, useState} from "react";
import {useAuth} from "@/contexts/AuthContext";

export interface PlayerInfo {
    steamId: string;
    personaName: string | null;
    avatar: string | null;
}

export interface PresenceSnapshot {
    totalCount: number;
    anonymousCount: number;
    players: PlayerInfo[];
}

const EMPTY_SNAPSHOT: PresenceSnapshot = {totalCount: 0, anonymousCount: 0, players: []};

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";
const MAX_RETRIES = 5;
const BASE_DELAY_MS = 1000;
const MAX_DELAY_MS = 30000;

function toWsOrigin(origin: string): string {
    if (origin.startsWith("https://")) return "wss://" + origin.slice("https://".length);
    if (origin.startsWith("http://")) return "ws://" + origin.slice("http://".length);
    return origin;
}

async function fetchTicket(): Promise<string | null> {
    try {
        const res = await fetch("/api/ws/ticket", {cache: "no-store", credentials: "include"});
        if (!res.ok) return null;
        const data = await res.json();
        return typeof data?.ticket === "string" ? data.ticket : null;
    } catch {
        return null;
    }
}

export function useRoundPresence(scopeKey: string | null): PresenceSnapshot & {connected: boolean} {
    const {isSignedIn} = useAuth();
    const [snapshot, setSnapshot] = useState<PresenceSnapshot>(EMPTY_SNAPSHOT);
    const [connected, setConnected] = useState(false);

    const socketRef = useRef<WebSocket | null>(null);
    const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const closedByUserRef = useRef(false);
    const retryCountRef = useRef(0);

    useEffect(() => {
        // Reset per-scope state.
        closedByUserRef.current = false;
        retryCountRef.current = 0;
        setSnapshot(EMPTY_SNAPSHOT);
        setConnected(false);

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

        const closeSocket = () => {
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

        const connect = async () => {
            if (disposed) return;
            const ticket = isSignedIn ? await fetchTicket() : null;
            if (disposed) return;

            const wsOrigin = toWsOrigin(BACKEND_ORIGIN);
            const url = `${wsOrigin}/ws/presence?scopeKey=${encodeURIComponent(scopeKey)}&ticket=${ticket ?? ""}`;

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
            };

            ws.onmessage = (ev) => {
                if (disposed) return;
                try {
                    const data = JSON.parse(ev.data) as Partial<PresenceSnapshot>;
                    setSnapshot({
                        totalCount: typeof data.totalCount === "number" ? data.totalCount : 0,
                        anonymousCount: typeof data.anonymousCount === "number" ? data.anonymousCount : 0,
                        players: Array.isArray(data.players) ? data.players : [],
                    });
                } catch (e) {
                    console.warn("[useRoundPresence] bad message", e);
                }
            };

            ws.onerror = (e) => {
                console.warn("[useRoundPresence] socket error", e);
                // Let onclose drive reconnect.
            };

            ws.onclose = () => {
                if (disposed) return;
                setConnected(false);
                socketRef.current = null;
                if (!closedByUserRef.current) scheduleReconnect();
            };
        };

        const scheduleReconnect = () => {
            if (disposed || closedByUserRef.current) return;
            if (retryCountRef.current >= MAX_RETRIES) return;
            const attempt = retryCountRef.current++;
            const delay = Math.min(BASE_DELAY_MS * Math.pow(2, attempt), MAX_DELAY_MS);
            clearReconnect();
            reconnectTimerRef.current = setTimeout(() => {
                reconnectTimerRef.current = null;
                void connect();
            }, delay);
        };

        void connect();

        return () => {
            disposed = true;
            closedByUserRef.current = true;
            clearReconnect();
            closeSocket();
            setConnected(false);
        };
    }, [scopeKey, isSignedIn]);

    return {...snapshot, connected};
}
