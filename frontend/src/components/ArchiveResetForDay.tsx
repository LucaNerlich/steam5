"use client";

import React, {useSyncExternalStore} from "react";
import {clearAll, clearDay, hasAny, hasAnyForDay} from "@/lib/storage";

let revision = 0;
const listeners = new Set<() => void>();
function notifyStorageChange() {
    revision++;
    for (const l of listeners) l();
}
function subscribeStorage(cb: () => void) {
    listeners.add(cb);
    return () => { listeners.delete(cb); };
}

export default function ArchiveResetForDay({date}: { date: string }): React.ReactElement | null {
    const snap = useSyncExternalStore(
        subscribeStorage,
        () => {
            void revision;
            return JSON.stringify({day: hasAnyForDay(date), global: hasAny()});
        },
        () => JSON.stringify({day: false, global: false})
    );
    let hasAnyDay = false;
    let hasAnyGlobal = false;
    try {
        const parsed = JSON.parse(snap) as { day?: unknown; global?: unknown };
        hasAnyDay = parsed?.day === true;
        hasAnyGlobal = parsed?.global === true;
    } catch {
        // Malformed snapshot: treat as no stored progress
    }

    const resetDay = () => {
        clearDay(date);
        notifyStorageChange();
    };

    const resetAll = () => {
        clearAll();
        notifyStorageChange();
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


