import React from "react";

/**
 * Renders the "Technical Info" heading with the controller support and
 * platform availability sections.
 */
export default function GameInfoTechnicalInfo({controllerSupport, windows, mac, linux}: {
    controllerSupport: string | null;
    windows: boolean;
    mac: boolean;
    linux: boolean;
}): React.ReactElement | null {
    const hasController = Boolean(controllerSupport);
    const hasPlatforms = Boolean(windows || mac || linux);

    if (!hasController && !hasPlatforms) return null;

    return (
        <>
            <h3>Technical Info</h3>

            {hasController && (
                <div className="game-info__section">
                    <h3>Controller</h3>
                    <p className="text-muted">{controllerSupport}</p>
                </div>
            )}

            {hasPlatforms && (
                <div className="game-info__section">
                    <h3>Platforms</h3>
                    <ul className="game-info__badges" aria-label="Supported platforms">
                        {windows && <li className="pill">Windows</li>}
                        {mac && <li className="pill">macOS</li>}
                        {linux && <li className="pill">Linux</li>}
                    </ul>
                </div>
            )}
        </>
    );
}
