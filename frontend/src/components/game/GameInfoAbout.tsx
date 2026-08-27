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
    if (url.startsWith("//")) return `https:${url}`;
    if (url.startsWith("http://")) return `https://${url.substring(7)}`;
    return url;
}

function isElement(n: DOMNode): n is Element {
    return (n as Element).type === "tag";
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
