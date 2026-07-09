import {redirect} from "next/navigation";
import {BACKEND_ORIGIN as backend} from "@/lib/backend";
import {Routes} from "../../routes";

export const dynamic = 'force-dynamic';

async function loadRandomArchiveDate(): Promise<string | null> {
    try {
        const res = await fetch(`${backend}/api/review-game/archive/random`, {
            cache: 'no-store',
            headers: {accept: 'application/json'},
        });
        if (!res.ok) return null;
        const data: {date?: string} = await res.json();
        return typeof data.date === 'string' ? data.date : null;
    } catch {
        return null;
    }
}

export default async function RandomArchivePage() {
    const date = await loadRandomArchiveDate();
    // Anchor past the round's game-info hero straight to the guess card's
    // heading, so players can start guessing immediately instead of scrolling.
    redirect(date ? `${Routes.archive}/${encodeURIComponent(date)}#guess-submission-1` : Routes.archive);
}
