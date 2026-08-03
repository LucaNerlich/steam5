import type {CSSProperties} from "react";
import "@/styles/components/avatar.css";

function initialsFor(name?: string | null): string {
    const trimmed = name?.trim();
    return trimmed ? trimmed.charAt(0).toUpperCase() : "?";
}

/**
 * Renders a player avatar image, or an initials fallback when no avatar is available.
 * Shared across the header, leaderboards, "playing now", and comments so every
 * avatar in the app looks and behaves the same.
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
