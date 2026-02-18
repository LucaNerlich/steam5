"use client";

import React, {useCallback, useSyncExternalStore} from "react";
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
    const {day: hasAnyDay, global: hasAnyGlobal} = JSON.parse(snap) as {day: boolean; global: boolean};

    const resetDay = useCallback(() => {
        clearDay(date);
        notifyStorageChange();
    }, [date]);

    const resetAll = useCallback(() => {
        clearAll();
        notifyStorageChange();
    }, []);

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


