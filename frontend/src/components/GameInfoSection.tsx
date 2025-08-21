import React from "react";
import Image from "next/image";
import parse, {
    attributesToProps,
    type DOMNode,
    domToReact,
    type Element,
    type HTMLReactParserOptions
} from "html-react-parser";
import type {SteamAppDetail} from "@/types/review-game";
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
                        {pick.movies.map((m, i) => (
                            <div key={`mov-${m.id}-${i}`} className="game-info__video">
                                <video controls preload="metadata" poster={m.thumbnail} className="video">
                                    {m.webm && <source src={m.webm} type="video/webm"/>}
                                    {m.mp4 && <source src={m.mp4} type="video/mp4"/>}
                                </video>
                                <div className="caption">{m.name}</div>
                            </div>
                        ))}
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
