"use client";

import {useEffect, useState} from "react";

/**
 * Returns a debounced copy of `value` that updates only after `delayMs`
 * has elapsed without `value` changing.
 *
 * @param value - The latest value to debounce.
 * @param delayMs - Milliseconds to wait after the last change before updating.
 * @returns The debounced value.
 */
export function useDebouncedValue<T>(value: T, delayMs: number): T {
    const [debounced, setDebounced] = useState(value);

    useEffect(() => {
        const timer = setTimeout(() => setDebounced(value), delayMs);
        return () => clearTimeout(timer);
    }, [value, delayMs]);

    return debounced;
}
