"use client";

import {createContext, useContext, type ReactNode} from "react";
import {useRoundPresence, type PresenceSnapshot} from "@/lib/hooks/useRoundPresence";

export type RoundPresenceState = PresenceSnapshot & {
    connected: boolean;
    reconnecting: boolean;
};

const RoundPresenceContext = createContext<RoundPresenceState | null>(null);

interface RoundPresenceProviderProps {
    scopeKey: string;
    children: ReactNode;
}

/**
 * Keeps one presence WebSocket alive across round navigation within the same game day.
 */
export function RoundPresenceProvider(props: Readonly<RoundPresenceProviderProps>): React.ReactElement {
    const {scopeKey, children} = props;
    const value = useRoundPresence(scopeKey);
    return (
        <RoundPresenceContext.Provider value={value}>
            {children}
        </RoundPresenceContext.Provider>
    );
}

export function useRoundPresenceContext(): RoundPresenceState {
    const ctx = useContext(RoundPresenceContext);
    if (!ctx) {
        throw new Error("useRoundPresenceContext must be used within RoundPresenceProvider");
    }
    return ctx;
}
