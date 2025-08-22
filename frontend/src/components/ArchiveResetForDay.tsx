"use client";

import React, {useEffect, useState} from "react";
import {clearAll, clearDay, hasAny, hasAnyForDay} from "@/lib/storage";

export default function ArchiveResetForDay({date}: { date: string }): React.ReactElement | null {
    const [hasAnyDay, setHasAnyDay] = useState(false);
    const [hasAnyGlobal, setHasAnyGlobal] = useState(false);

    useEffect(() => {
        setHasAnyDay(hasAnyForDay(date));
        setHasAnyGlobal(hasAny());
    }, [date]);

    if (!hasAnyDay && !hasAnyGlobal) return null;

    return (
        <div className="archive__reset">
            {hasAnyDay && (
                <button className="btn-ghost" onClick={() => {
                    clearDay(date);
                    setHasAnyDay(false);
                }}>Reset this day</button>
            )}
            {hasAnyGlobal && (
                <button className="btn-ghost" onClick={() => {
                    clearAll();
                    setHasAnyGlobal(false);
                    setHasAnyDay(false);
                }}>Reset all archive progress</button>
            )}
        </div>
    );
}


