"use client";

import React from "react";
import Image from "next/image";
import {Fancybox} from "@fancyapps/ui";
import "@fancyapps/ui/dist/fancybox/fancybox.css";
import parse, {
    attributesToProps,
    type DOMNode,
    domToReact,
    type Element,
    type HTMLReactParserOptions
} from "html-react-parser";
import type {SteamAppDetail, Movie as SteamMovie} from "@/types/review-game";
import "@/styles/components/gameInfoSection.css";

interface Props {
    pick: SteamAppDetail;
}

export default function GameInfoSection({pick}: Props): React.ReactElement | null {
    // Hooks must be called unconditionally
    const uniqueCategories = React.useMemo(() => {
        const list = Array.isArray(pick?.categories) ? pick!.categories : [];
        const seen = new Set<string>();
        return list.filter((c) => {
            const key = c?.id != null ? String(c.id) : c?.description?.toLowerCase().trim() || "";
            if (!key || seen.has(key)) return false;
            seen.add(key);
            return true;
        });
    }, [pick]);

    const hasMovies = Array.isArray(pick?.movies) && pick!.movies.length > 0;
    const hasCategories = uniqueCategories.length > 0;
    const hasShort = Boolean(pick?.shortDescription);
    const hasAbout = Boolean(pick?.aboutTheGame);
    const hasController = Boolean(pick?.controllerSupport);
    const hasPlatforms = Boolean(pick?.windows || pick?.mac || pick?.linux);
    const hasAny = Boolean(
        pick &&
        (hasMovies || hasCategories || hasShort || hasAbout || hasController || hasPlatforms)
    );

    const aboutContent = React.useMemo(() => {
        if (!hasAbout) return null;
        const html = pick.aboutTheGame || "";
        const normalizeToHttps = (url: string): string => {
            if (!url) return url;
            if (url.startsWith("//")) return `https:${url}`;
            if (url.startsWith("http://")) return `https://${url.substring(7)}`;
            return url;
        };
        const isElement = (n: DOMNode): n is Element => {
            return (n as Element).type === "tag";
        };
        const options: HTMLReactParserOptions = {
            replace: (node: DOMNode) => {
                if (!isElement(node)) return undefined;
                const el: Element = node;
                if (el.name === "img") {
                    const srcRaw = el.attribs?.src || "";
                    if (!srcRaw) return undefined;
                    const src = normalizeToHttps(srcRaw);
                    const alt = el.attribs?.alt || "";
                    const widthAttr = el.attribs?.width;
                    const heightAttr = el.attribs?.height;
                    const width = widthAttr != null ? Number.parseInt(widthAttr, 10) : undefined;
                    const height = heightAttr != null ? Number.parseInt(heightAttr, 10) : undefined;
                    const title = el.attribs?.title as string | undefined;
                    const sizes = el.attribs?.sizes as string | undefined;

                    if (Number.isFinite(width) && Number.isFinite(height)) {
                        return (
                            <Image
                                src={src}
                                alt={alt}
                                width={width as number}
                                height={height as number}
                                loading="lazy"
                                title={title}
                                sizes={sizes}
                            />
                        );
                    }
                    // Fallback to plain <img> when intrinsic size is unavailable
                    return (
                        <>
                            {/* eslint-disable-next-line @next/next/no-img-element */}
                            <img src={src} alt={alt} loading="lazy" title={title}/>
                        </>
                    );
                }
                // For all other elements, strip class/className
                if (el.type === "tag") {
                    if (el.name === "script" || el.name === "style") {
                        return <></>;
                    }
                    const attribs: Record<string, string> = {...(el.attribs || {})};
                    // strip classes and potentially dangerous attributes
                    delete attribs.class;
                    delete attribs.className;
                    delete attribs.style;
                    for (const key of Object.keys(attribs)) {
                        if (key.toLowerCase().startsWith("on")) {
                            delete attribs[key];
                        }
                    }
                    const props = attributesToProps(attribs);
                    // Void elements must not receive children
                    const voidElements = new Set([
                        "area", "base", "br", "col", "embed", "hr", "img", "input", "keygen", "link", "meta", "param", "source", "track", "wbr"
                    ]);
                    if (voidElements.has(el.name)) {
                        return React.createElement(el.name, props);
                    }
                    return React.createElement(el.name, props, domToReact(el.children as unknown as DOMNode[], options));
                }
                return undefined;
            },
        };
        return parse(html, options);
    }, [hasAbout, pick?.aboutTheGame]);

    // Helper to ensure URLs are absolute
    const normalizeUrl = (url: string | null | undefined): string => {
        if (!url) return "";
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        if (url.startsWith("//")) return `https:${url}`;
        return url;
    };

    const resolveMovieSource = (movie: SteamMovie) => {
        const ordered = [movie.dashAv1, movie.dashH264, movie.hlsH264, movie.mp4, movie.webm];
        const rawSrc = ordered.find((entry) => Boolean(entry));
        const normalizedSrc = normalizeUrl(rawSrc as string | null | undefined);
        if (!normalizedSrc) {
            return {src: "", type: undefined as string | undefined, format: undefined as string | undefined};
        }
        const lower = normalizedSrc.toLowerCase();
        if (lower.includes(".mpd")) {
            return {src: normalizedSrc, type: "html5video", format: "application/dash+xml"};
        }
        if (lower.includes(".m3u8")) {
            return {src: normalizedSrc, type: "html5video", format: "application/vnd.apple.mpegurl"};
        }
        return {src: normalizedSrc, type: undefined as string | undefined, format: undefined as string | undefined};
    };

    React.useEffect(() => {
        if (!hasMovies) return;

        let dashModulePromise: Promise<any> | null = null;
        const dashPlayers: Array<{ reset: () => void }> = [];
        const selector = `[data-fancybox="videos-${pick.appId}"]`;

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

        // @ts-ignore Fancybox global binding
        Fancybox.bind(selector, {
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
                    void loadDashModule().then((dashjsModule) => {
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

        return () => {
            Fancybox.unbind(selector);
            Fancybox.close();
            disposePlayers();
        };
    }, [hasMovies, pick.appId]);

    if (!pick || !hasAny) return null;

    return (
        <section className="game-info" aria-labelledby="more-about-this-game">
            <h2 id="more-about-this-game" className="game-info__title">More about this game</h2>

            {hasCategories && (
                <div className="game-info__section">
                    <h3>Categories</h3>
                    <ul className="game-info__pills" aria-label="Categories">
                        {uniqueCategories.map((c) => (
                            <li key={`cat-${c.id ?? c.description?.toLowerCase().trim()}`} className="pill">
                                {c.description}
                            </li>
                        ))}
                    </ul>
                </div>
            )}

            {hasShort && (
                <div className="game-info__section">
                    <h3>Short description</h3>
                    <p>{pick.shortDescription}</p>
                </div>
            )}

            {hasAbout && (
                <div className="game-info__section">
                    <h3>About the game</h3>
                    <div className="game-info__about">{aboutContent}</div>
                </div>
            )}

            {hasMovies && (
                <div className="game-info__section">
                    <h3>Videos</h3>
                    <div className="game-info__videos">
                        {pick.movies.map((m, i) => {
                            const {src: videoSrc, type: fancyboxType, format: fancyboxFormat} = resolveMovieSource(m);
                            const thumbSrc = normalizeUrl(m.thumbnail);
                            if (!videoSrc || !thumbSrc) return null;
                            return (
                                <div key={`mov-${m.id}-${i}`} className="game-info__video">
                                    <a
                                        href={videoSrc || "#"}
                                        data-fancybox={`videos-${pick.appId}`}
                                        data-caption={m.name}
                                        data-thumb={thumbSrc}
                                        data-type={fancyboxType}
                                        data-html5video-format={fancyboxFormat}
                                        className="video-link"
                                    >
                                        <div className="video-thumbnail">
                                            {/* eslint-disable-next-line @next/next/no-img-element */}
                                            <img src={thumbSrc} alt={m.name} loading="lazy"/>
                                            <div className="video-play-icon">▶</div>
                                        </div>
                                    </a>
                                    <div className="caption">{m.name}</div>
                                </div>
                            );
                        })}
                    </div>
                </div>
            )}

            {(hasController || hasPlatforms) && (
                <h2>Technical Info</h2>
            )}

            {hasController && (
                <div className="game-info__section">
                    <h3>Controller</h3>
                    <p className="text-muted">{pick.controllerSupport}</p>
                </div>
            )}

            {hasPlatforms && (
                <div className="game-info__section">
                    <h3>Platforms</h3>
                    <ul className="game-info__badges" aria-label="Supported platforms">
                        {pick.windows && <li className="pill">Windows</li>}
                        {pick.mac && <li className="pill">macOS</li>}
                        {pick.linux && <li className="pill">Linux</li>}
                    </ul>
                </div>
            )}
        </section>
    );
}
