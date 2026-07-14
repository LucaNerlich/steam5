import type {ReactNode} from "react";
import {RoundPresenceProvider} from "@/contexts/RoundPresenceContext";
import {SITE_PRESENCE_SCOPE_KEY} from "@/lib/presence";

/**
 * Provides review-guesser content with site-wide presence (all games share one pool).
 */
export default function ReviewGuesserRoundLayout({children}: {children: ReactNode}) {
    return (
        <RoundPresenceProvider scopeKey={SITE_PRESENCE_SCOPE_KEY}>
            {children}
        </RoundPresenceProvider>
    );
}
