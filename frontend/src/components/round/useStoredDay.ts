"use client";

import {useSyncExternalStore} from "react";
import type {StoredDay} from "@/lib/storage";

// localStorage is an external system React cannot observe, so the stored day is
// read through useSyncExternalStore instead of being mirrored into component
// state by an effect (a mirror costs an extra render per write and can drift).
//
// The snapshot is cached because getSnapshot must return a stable value between
// writes; it is validated against the raw localStorage string, which also picks
// up same-tab writes made by other components (e.g. the offline archive).
// Cross-tab writes arrive through the native "storage" event, our own writes
// through notifyStoredDayChanged().

// Keep in sync with storageKeyForDate() in lib/storage.ts.
const STORAGE_KEY_PREFIX = "review-guesser:";

const STORED_DAY_EVENT = "s5:stored-day-changed";

// Map-based cache keyed by gameDate so multiple dates can have stable snapshots.
const cache = new Map<string, {raw: string | null; value: StoredDay | null}>();

function readRaw(gameDate: string): string | null {
    try {
        return window.localStorage.getItem(STORAGE_KEY_PREFIX + gameDate);
    } catch {
        return null;
    }
}

function parseDay(raw: string | null): StoredDay | null {
    if (!raw) return null;
    try {
        return JSON.parse(raw) as StoredDay;
    } catch {
        return null;
    }
}

function readSnapshot(gameDate: string): StoredDay | null {
    const raw = readRaw(gameDate);
    const cached = cache.get(gameDate);
    if (!cached || cached.raw !== raw) {
        const entry = {raw, value: parseDay(raw)};
        cache.set(gameDate, entry);
        return entry.value;
    }
    return cached.value;
}

function notifySubscribers(): void {
    cache.clear();
    for (const onStoreChange of subscribers) onStoreChange();
}

const subscribers = new Set<() => void>();

function subscribe(onStoreChange: () => void): () => void {
    subscribers.add(onStoreChange);
    const handleStorage = (event: StorageEvent) => {
        if (event.storageArea === window.localStorage) notifySubscribers();
    };
    const handleVisibilityChange = () => {
        if (document.visibilityState === "visible") {
            cache.clear();
            for (const cb of subscribers) cb();
        }
    };
    window.addEventListener(STORED_DAY_EVENT, notifySubscribers);
    window.addEventListener("storage", handleStorage);
    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => {
        subscribers.delete(onStoreChange);
        window.removeEventListener(STORED_DAY_EVENT, notifySubscribers);
        window.removeEventListener("storage", handleStorage);
        document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
}

/**
 * Must be called after writing the stored day (e.g. via saveRound) so readers
 * of {@link useStoredDay} re-render with the fresh snapshot.
 */
export function notifyStoredDayChanged(): void {
    if (typeof window === "undefined") return;
    notifySubscribers();
    window.dispatchEvent(new Event(STORED_DAY_EVENT));
}

/**
 * Live snapshot of the day's stored results from localStorage.
 *
 * @param gameDate - Date identifying the day; null snapshot when absent.
 */
export default function useStoredDay(gameDate: string | undefined): StoredDay | null {
    return useSyncExternalStore(
        subscribe,
        () => (gameDate ? readSnapshot(gameDate) : null),
        () => null,
    );
}
