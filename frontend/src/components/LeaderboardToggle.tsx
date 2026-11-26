"use client";

import Link from "next/link";
import {usePathname} from "next/navigation";
import {useLayoutEffect, useRef, useState} from "react";

import {LEADERBOARD_ROUTES} from "@/lib/leaderboardRoutes";

function isActive(pathname: string, href: string): boolean {
    if (href === "/review-guesser/leaderboard") {
        return pathname === href;
    }
    return pathname.startsWith(href);
}

type IndicatorGeometry = {
    left: number;
    top: number;
    width: number;
    height: number;
    ready: boolean;
};

const INITIAL_GEOMETRY: IndicatorGeometry = {left: 0, top: 0, width: 0, height: 0, ready: false};

export default function LeaderboardToggle() {
    const pathname = usePathname();
    const navRef = useRef<HTMLElement | null>(null);
    const linkRefs = useRef<Array<HTMLAnchorElement | null>>([]);
    const [indicator, setIndicator] = useState<IndicatorGeometry>(INITIAL_GEOMETRY);

    const activeIndex = LEADERBOARD_ROUTES.findIndex((route) => isActive(pathname, route.href));

    useLayoutEffect(() => {
        const updateIndicator = () => {
            const activeLink = activeIndex >= 0 ? linkRefs.current[activeIndex] : null;
            if (!activeLink) {
                return;
            }
            setIndicator({
                left: activeLink.offsetLeft,
                top: activeLink.offsetTop,
                width: activeLink.offsetWidth,
                height: activeLink.offsetHeight,
                ready: true,
            });
        };

        updateIndicator();

        const nav = navRef.current;
        if (!nav || typeof ResizeObserver === "undefined") {
            return;
        }

        const observer = new ResizeObserver(() => updateIndicator());
        observer.observe(nav);
        window.addEventListener("resize", updateIndicator);

        return () => {
            observer.disconnect();
            window.removeEventListener("resize", updateIndicator);
        };
    }, [activeIndex]);

    return (
        <nav className="leaderboard__toggle" aria-label="Leaderboard view" ref={navRef}>
            <span
                aria-hidden="true"
                className={`leaderboard__toggle-indicator${indicator.ready ? " is-ready" : ""}`}
                style={{
                    transform: `translate(${indicator.left}px, ${indicator.top}px)`,
                    width: `${indicator.width}px`,
                    height: `${indicator.height}px`,
                }}
            />
            {LEADERBOARD_ROUTES.map((route, index) => (
                <Link
                    key={route.href}
                    ref={(el) => {
                        linkRefs.current[index] = el;
                    }}
                    href={route.href}
                    aria-current={isActive(pathname, route.href) ? "page" : undefined}
                    className={`btn-ghost leaderboard__toggle-btn${isActive(pathname, route.href) ? " is-active" : ""}`}
                >
                    {route.label}
                </Link>
            ))}
        </nav>
    );
}
