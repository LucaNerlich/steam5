import React from "react";
import Image from "next/image";
import parse, {
    attributesToProps,
    type DOMNode,
    domToReact,
    type Element,
    type HTMLReactParserOptions
} from "html-react-parser";

function normalizeToHttps(url: string): string {
    if (!url) return url;
    if (url.startsWith("https://")) return url;
    if (url.startsWith("//")) return `https:${url}`;
    if (url.startsWith("http://")) return `https://${url.substring(7)}`;
    // Reject other unsupported schemes
    if (url.includes("://")) return "";
    return url;
}

function isElement(n: DOMNode): n is Element {
    return (n as Element).type === "tag";
}

// Allowlist of safe HTML elements that can be rendered from Steam's about HTML
const SAFE_ELEMENTS = new Set([
    "a", "abbr", "address", "article", "aside", "b", "blockquote", "br", "caption",
    "cite", "code", "col", "colgroup", "dd", "del", "details", "dfn", "div", "dl",
    "dt", "em", "figcaption", "figure", "footer", "h1", "h2", "h3", "h4", "h5", "h6",
    "header", "hr", "i", "img", "ins", "kbd", "li", "main", "mark", "nav", "ol", "p",
    "pre", "q", "s", "samp", "section", "small", "span", "strong", "sub", "summary",
    "sup", "table", "tbody", "td", "tfoot", "th", "thead", "time", "tr", "u", "ul", "var", "wbr"
]);

// Allowlist of safe HTML attributes
const SAFE_ATTRIBUTES = new Set([
    "alt", "aria-label", "aria-labelledby", "cellpadding", "cellspacing", "colspan",
    "datetime", "height", "href", "id", "lang", "rowspan", "sizes", "src", "srcset",
    "target", "title", "width"
]);

// Safe URL protocols for href/src attributes
const SAFE_PROTOCOLS = new Set(["http:", "https:"]);

function isSafeUrl(url: string): boolean {
    if (!url) return false;
    try {
        const parsed = new URL(url, "https://example.com");
        return SAFE_PROTOCOLS.has(parsed.protocol);
    } catch {
        // Relative URLs are safe (they resolve to the current origin)
        return !url.includes(":");
    }
}

/**
 * Parses the sanitized "about the game" HTML from Steam into React elements,
 * rewriting images to next/image where their intrinsic size is known.
 */
function parseAboutHtml(html: string): ReturnType<typeof parse> {
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
                // Fallback when Steam's HTML has no intrinsic size: render an optimized
                // next/image at natural size (0×0 + auto CSS = attribute-free <img>
                // sizing; .game-info__section img CSS still applies).
                return (
                    <Image
                        src={src}
                        alt={alt}
                        width={0}
                        height={0}
                        loading="lazy"
                        title={title}
                        style={{width: "auto", height: "auto"}}
                    />
                );
            }
            // For all other elements, validate against allowlist and sanitize attributes
            if (el.type === "tag") {
                // Reject dangerous elements
                if (!SAFE_ELEMENTS.has(el.name) || el.name === "script" || el.name === "style" || el.name === "iframe") {
                    return <></>;
                }

                // Sanitize attributes
                const attribs: Record<string, string> = {};
                for (const [key, value] of Object.entries(el.attribs || {})) {
                    const lowerKey = key.toLowerCase();
                    // Skip unsafe attributes
                    if (!SAFE_ATTRIBUTES.has(lowerKey)) continue;
                    if (lowerKey.startsWith("on")) continue;
                    // Validate and normalize URL-bearing attributes
                    if (lowerKey === "href" || lowerKey === "src" || lowerKey === "srcset") {
                        if (!isSafeUrl(value)) continue;
                        attribs[key] = normalizeToHttps(value);
                    } else {
                        attribs[key] = value;
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
}

/**
 * Renders the "About the game" section from Steam's about HTML.
 */
export default function GameInfoAbout({html}: {
    html: string;
}): React.ReactElement {
    return (
        <div className="game-info__section">
            <h3>About the game</h3>
            <div className="game-info__about">{parseAboutHtml(html)}</div>
        </div>
    );
}
