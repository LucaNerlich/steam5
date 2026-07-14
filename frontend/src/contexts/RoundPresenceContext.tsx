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
 * Provides round presence state to descendant components for the specified scope.
 *
 * @param props - Provider properties, including the presence scope and descendant content.
 * @returns The context provider element.
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

/**
 * Accesses the presence state provided by the nearest round presence provider.
 *
 * @returns The current round presence state
 * @throws An error if used outside a `RoundPresenceProvider`
 */
export function useRoundPresenceContext(): RoundPresenceState {
    const ctx = useContext(RoundPresenceContext);
    if (!ctx) {
        throw new Error("useRoundPresenceContext must be used within RoundPresenceProvider");
    }
    return ctx;
}
