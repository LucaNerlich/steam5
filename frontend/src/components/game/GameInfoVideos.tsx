"use client";

import React, {useEffect, useRef} from "react";
import Image from "next/image";
import type {Movie as SteamMovie} from "@/types/review-game";

// Helper to ensure URLs are absolute and use HTTPS
function normalizeUrl(url: string | null | undefined): string {
    if (!url) return "";
    if (url.startsWith("https://")) return url;
    if (url.startsWith("//")) return `https:${url}`;
    if (url.startsWith("http://")) return `https://${url.substring(7)}`;
    // Reject other unsupported schemes
    if (url.includes("://")) return "";
    return url;
}

function resolveMovieSource(movie: SteamMovie): {
    src: string;
    type: string | undefined;
    format: string | undefined;
} {
    const ordered = [movie.dashAv1, movie.dashH264, movie.hlsH264, movie.mp4, movie.webm];
    const rawSrc = ordered.find((entry) => Boolean(entry));
    const normalizedSrc = normalizeUrl(rawSrc as string | null | undefined);
    if (!normalizedSrc) {
        return {src: "", type: undefined, format: undefined};
    }
    const lower = normalizedSrc.toLowerCase();
    if (lower.includes(".mpd")) {
        return {src: normalizedSrc, type: "html5video", format: "application/dash+xml"};
    }
    if (lower.includes(".m3u8")) {
        return {src: normalizedSrc, type: "html5video", format: "application/vnd.apple.mpegurl"};
    }
    return {src: normalizedSrc, type: undefined, format: undefined};
}

// Module-level lazy singletons so the dynamic imports stay out of the
// component/effect body (the React Compiler can't optimize them there).
let dashModulePromise: Promise<any> | null = null;

const loadDashModule = (): Promise<any> => {
    if (dashModulePromise) return dashModulePromise;
    dashModulePromise = import("dashjs")
        .then((mod) => mod ?? null)
        .catch((err) => {
            console.error("Failed to load dash.js", err);
            return null;
        });
    return dashModulePromise;
};

const loadFancybox = async (): Promise<any | null> => {
    try {
        const fancyboxModule = await import("@fancyapps/ui");
        return fancyboxModule.Fancybox;
    } catch (error) {
        console.error("Failed to load Fancybox", error);
        return null;
    }
};

/**
 * Renders the game's video thumbnails and wires up Fancybox with dash.js
 * playback for DASH streams.
 */
export default function GameInfoVideos({appId, movies}: {
    appId: number;
    movies: SteamMovie[];
}): React.ReactElement | null {
    const hasMovies = movies.length > 0;
    // Ref instead of a captured `let`: the Compiler can't optimize update
    // expressions on variables captured within lambdas (the Fancybox handlers
    // bump this counter). A ref is a plain property write. Declared at the
    // component top level — hooks cannot live inside the effect body.
    const playerGenerationRef = useRef(0);

    useEffect(() => {
        if (!hasMovies) return;

        let disposed = false;
        let FancyboxInstance: any = null;
        const dashPlayers: Array<{ reset: () => void }> = [];
        const selector = `[data-fancybox="videos-${appId}"]`;

        const disposePlayers = () => {
            while (dashPlayers.length > 0) {
                const player = dashPlayers.pop();
                try {
                    player?.reset();
                } catch {
                    // ignore cleanup failures
                }
            }
        };

        const initFancybox = async () => {
            const Fancybox = await loadFancybox();
            if (disposed || !Fancybox) return;
            FancyboxInstance = Fancybox;

            // @ts-ignore Fancybox global binding
            FancyboxInstance.bind(selector, {
                Toolbar: {
                    display: {
                        left: ["infobar"],
                        middle: [],
                        right: ["close"]
                    }
                },
                on: {
                    "Carousel.attachSlideEl": (_fancyboxRef: unknown, _carouselRef: unknown, slide: { src?: string; el?: HTMLElement }) => {
                        const src = typeof slide?.src === "string" ? slide.src : "";
                        if (!src.toLowerCase().includes(".mpd")) return;
                        const videoEl = slide.el?.querySelector("video") as HTMLVideoElement | null;
                        if (!videoEl) return;
                        const generation = ++playerGenerationRef.current;
                        void loadDashModule().then((dashjsModule) => {
                            if (disposed || generation !== playerGenerationRef.current) return;
                            if (!dashjsModule) return;
                            const dashLib = dashjsModule.default ?? dashjsModule;
                            if (!dashLib?.MediaPlayer) return;
                            const player = dashLib.MediaPlayer().create();
                            player.initialize(videoEl, src, true);
                            player.setMute(true);
                            dashPlayers.push(player);
                        });
                    },
                    destroy: () => {
                        disposePlayers();
                    }
                }
            });
        };

        // Initialize Fancybox when needed
        void initFancybox();

        return () => {
            disposed = true;
            if (FancyboxInstance) {
                FancyboxInstance.unbind(selector);
                FancyboxInstance.close();
            }
            disposePlayers();
        };
    }, [hasMovies, appId]);

    if (!hasMovies) return null;

    return (
        <div className="game-info__section">
            <h3>Videos</h3>
            <div className="game-info__videos">
                {movies.map((m) => {
                    const {src: videoSrc, type: fancyboxType, format: fancyboxFormat} = resolveMovieSource(m);
                    const thumbSrc = normalizeUrl(m.thumbnail);
                    if (!videoSrc || !thumbSrc) return null;
                    return (
                        <div key={`mov-${m.id}`} className="game-info__video">
                            <a
                                href={videoSrc || "#"}
                                data-fancybox={`videos-${appId}`}
                                data-caption={m.name}
                                data-thumb={thumbSrc}
                                data-type={fancyboxType}
                                data-html5video-format={fancyboxFormat}
                                className="video-link"
                            >
                                <div className="video-thumbnail">
                                    <Image
                                        src={thumbSrc}
                                        alt={m.name}
                                        fill
                                        sizes="(max-width: 640px) 100vw, 420px"
                                    />
                                    <div className="video-play-icon">▶</div>
                                </div>
                            </a>
                            <div className="caption">{m.name}</div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
