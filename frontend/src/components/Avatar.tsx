import type {CSSProperties} from "react";
import "@/styles/components/avatar.css";

/**
 * Extracts the uppercase initial from a name.
 *
 * @param name - The name from which to derive the initial
 * @returns The uppercase first character of the trimmed name, or `"?"` when the name is absent or blank
 */
function initialsFor(name?: string | null): string {
    const trimmed = name?.trim();
    return trimmed ? trimmed.charAt(0).toUpperCase() : "?";
}

/**
 * Renders an avatar image or an initials fallback.
 *
 * @param src - The avatar image URL
 * @param name - The name used for the title and initials fallback
 * @param size - The avatar dimensions in pixels
 * @param className - Additional CSS classes
 */
export default function Avatar({src, name, size = 32, className}: {
    src?: string | null;
    name?: string | null;
    size?: number;
    className?: string;
}): React.ReactElement {
    const style: CSSProperties = {width: size, height: size};
    const title = name || "Player";
    const classes = `avatar${className ? ` ${className}` : ""}`;

    if (src) {
        return (
            <img
                className={classes}
                style={style}
                src={src}
                alt=""
                title={title}
                width={size}
                height={size}
                loading="lazy"
                referrerPolicy="no-referrer"
            />
        );
    }

    return (
        <span className={classes} style={style} title={title} aria-hidden="true">
            {initialsFor(name)}
        </span>
    );
}
