"use client";

import React from "react";
import dynamic from "next/dynamic";
import type {SteamAppDetail} from "@/types/review-game";

const DynamicGameInfoSection = dynamic(() => import("@/components/GameInfoSection"), {
    ssr: false,
    loading: () => <p>Loading Game Metadata ...</p>,
});

export default function LazyGameInfoSection({pick}: { pick: SteamAppDetail }): React.ReactElement {
    const ref = React.useRef<HTMLDivElement | null>(null);
    const [inView, setInView] = React.useState(false);

    React.useEffect(() => {
        if (!ref.current || inView) return;
        let observer: IntersectionObserver | null = new IntersectionObserver(
            (entries) => {
                for (const entry of entries) {
                    if (entry.isIntersecting) {
                        setInView(true);
                        if (observer) observer.disconnect();
                        break;
                    }
                }
            },
            {root: null, rootMargin: "200px 0px", threshold: 0.1}
        );
        observer.observe(ref.current);
        return () => {
            if (observer) observer.disconnect();
        };
    }, [inView]);

    return (
        <div ref={ref} aria-busy={!inView} aria-live="polite">
            {inView ? <DynamicGameInfoSection pick={pick}/> : null}
        </div>
    );
}


