import type {ReactNode} from "react";
import {RoundPresenceProvider} from "@/contexts/RoundPresenceContext";
import {SITE_PRESENCE_SCOPE_KEY} from "@/lib/presence";

export default function YearGuesserRoundLayout({children}: {children: ReactNode}) {
    return (
        <RoundPresenceProvider scopeKey={SITE_PRESENCE_SCOPE_KEY}>
            {children}
        </RoundPresenceProvider>
    );
}
