"use client";

import React from "react";
import {clearAll, clearDay, hasAny} from "@/lib/storage";
import useStoredDay, {notifyStoredDayChanged} from "@/components/round/useStoredDay";

/**
 * Reset controls for the offline archive.
 *
 * <p>Reads the shared stored-day store instead of keeping a private listener/revision pair: the
 * local copy never heard about the guesses `ArchiveOfflineRound` writes, so a saved round left the
 * controls hidden until a reload. Any store write clears the snapshot cache, so this also re-renders
 * for progress on *other* days — which the "reset all" control depends on.</p>
 */
export default function ArchiveResetForDay({date}: { date: string }): React.ReactElement | null {
    const stored = useStoredDay(date);
    const hasAnyDay = Boolean(stored?.results && Object.keys(stored.results).length > 0);
    const hasAnyGlobal = hasAny();

    const resetDay = () => {
        clearDay(date);
        notifyStoredDayChanged();
    };

    const resetAll = () => {
        clearAll();
        notifyStoredDayChanged();
    };

    if (!hasAnyDay && !hasAnyGlobal) return null;

    return (
        <div className="archive__reset">
            {hasAnyDay && (
                <button className="btn-ghost" onClick={resetDay}>Reset this day</button>
            )}
            {hasAnyGlobal && (
                <button className="btn-ghost" onClick={resetAll}>Reset all archive progress</button>
            )}
        </div>
    );
}
