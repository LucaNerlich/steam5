import type {Metadata} from "next";
import Link from "next/link";
import "@/styles/components/archive-list.css";
import ArchiveSummary from "@/components/ArchiveSummary";
import GameStatistics from "@/components/GameStatistics";

const ONE_DAY_REVALIDATE_SECONDS = 86400;
const IMMUTABLE_REVALIDATE_SECONDS = 31536000;
const ARCHIVE_DAY_LIST_LIMIT = 5000;

export const revalidate = 86400;

async function loadDays(): Promise<string[]> {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    try {
        const res = await fetch(`${backend}/api/review-game/days?limit=${ARCHIVE_DAY_LIST_LIMIT}`, {
            headers: {'accept': 'application/json'},
            next: {revalidate: ONE_DAY_REVALIDATE_SECONDS},
        });
        if (!res.ok) return [];
        const data: unknown = await res.json();
        if (!Array.isArray(data)) return [];
        return data.filter((date): date is string => typeof date === "string");
    } catch {
        return [];
    }
}

async function loadBuckets(): Promise<{ buckets: string[]; bucketTitles: string[] }> {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    try {
        const res = await fetch(`${backend}/api/review-game/buckets`, {
            headers: {'accept': 'application/json'},
            next: {revalidate: 86400},
        });
        if (!res.ok) return {buckets: [], bucketTitles: []};
        return res.json();
    } catch {
        return {buckets: [], bucketTitles: []};
    }
}

async function loadDaySummary(
    date: string,
    revalidateSeconds: number
): Promise<Array<{ appId: number; name: string }>> {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    try {
        const res = await fetch(`${backend}/api/review-game/day/${encodeURIComponent(date)}`, {
            headers: {accept: 'application/json'},
            next: {revalidate: revalidateSeconds},
        });
        if (!res.ok) return [];
        const data: { picks?: Array<{ appId: number; name: string }> } = await res.json();
        return Array.isArray(data?.picks) ? data.picks : [];
    } catch {
        return [];
    }
}

function getMonthKey(date: string): string {
    return date.slice(0, 7);
}

function isValidMonth(month: string): boolean {
    return /^\d{4}-(0[1-9]|1[0-2])$/.test(month);
}

function getMonthLabel(month: string): string {
    const [year, monthNum] = month.split("-");
    const asDate = new Date(Date.UTC(Number(year), Number(monthNum) - 1, 1));
    return new Intl.DateTimeFormat("en-US", {month: "long", year: "numeric", timeZone: "UTC"}).format(asDate);
}

function getMonthHref(month: string): string {
    return `/review-guesser/archive?month=${encodeURIComponent(month)}`;
}

type ArchivePageProps = {
    searchParams?: Promise<{ month?: string | string[] }>;
};

export default async function ArchiveIndexPage({searchParams}: ArchivePageProps) {
    const query = searchParams ? await searchParams : {};
    const requestedMonthRaw = Array.isArray(query.month) ? query.month[0] : query.month;
    const requestedMonth = typeof requestedMonthRaw === "string" ? requestedMonthRaw.trim() : "";
    let days = await loadDays();
    const todayStr = new Date().toISOString().slice(0, 10);
    days = (days || []).filter(date => date !== todayStr);
    const months = Array.from(
        new Set(
            days
                .map(getMonthKey)
                .filter(isValidMonth)
        )
    ).sort((a, b) => b.localeCompare(a));
    const fallbackMonth = months[0] ?? "";
    const selectedMonth = (isValidMonth(requestedMonth) && months.includes(requestedMonth))
        ? requestedMonth
        : fallbackMonth;
    const selectedMonthIndex = months.findIndex((month) => month === selectedMonth);
    const newerMonth = selectedMonthIndex > 0 ? months[selectedMonthIndex - 1] : null;
    const olderMonth = selectedMonthIndex >= 0 && selectedMonthIndex < months.length - 1
        ? months[selectedMonthIndex + 1]
        : null;
    const visibleDays = selectedMonth ? days.filter((date) => getMonthKey(date) === selectedMonth) : [];
    const now = new Date();
    const currentMonth = `${now.getUTCFullYear()}-${String(now.getUTCMonth() + 1).padStart(2, "0")}`;
    const selectedMonthRevalidate = selectedMonth === currentMonth
        ? ONE_DAY_REVALIDATE_SECONDS
        : IMMUTABLE_REVALIDATE_SECONDS;
    const bucketData = await loadBuckets();

    return (
        <section className="container">
            <h1>Archive</h1>
            <GameStatistics/>
            {bucketData.buckets.length > 0 && (
                <ArchiveSummary
                    bucketLabels={bucketData.buckets}
                    bucketTitles={bucketData.bucketTitles}
                />
            )}
            {days.length === 0 ? (
                <p className="text-muted">No previous daily challenges found.</p>
            ) : (
                <>
                    <nav className="archive-list__month-nav" aria-label="Archive month navigation">
                        <div className="archive-list__month-nav-row">
                            {newerMonth ? (
                                <Link className="archive-list__month-link" href={getMonthHref(newerMonth)} scroll={false}>
                                    Newer month
                                </Link>
                            ) : (
                                <span className="archive-list__month-link archive-list__month-link--disabled" aria-disabled="true">
                                    Newer month
                                </span>
                            )}
                            <strong>{selectedMonth ? getMonthLabel(selectedMonth) : "Archive"}</strong>
                            {olderMonth ? (
                                <Link className="archive-list__month-link" href={getMonthHref(olderMonth)} scroll={false}>
                                    Older month
                                </Link>
                            ) : (
                                <span className="archive-list__month-link archive-list__month-link--disabled" aria-disabled="true">
                                    Older month
                                </span>
                            )}
                        </div>
                        <ol className="archive-list__month-chips">
                            {months.map((month) => (
                                <li key={month}>
                                    <Link
                                        href={getMonthHref(month)}
                                        scroll={false}
                                        className={month === selectedMonth
                                            ? "archive-list__month-chip archive-list__month-chip--active"
                                            : "archive-list__month-chip"}
                                        aria-current={month === selectedMonth ? "page" : undefined}
                                    >
                                        {getMonthLabel(month)}
                                    </Link>
                                </li>
                            ))}
                        </ol>
                    </nav>
                    {visibleDays.length === 0 ? (
                        <p className="text-muted">No previous daily challenges found for this month.</p>
                    ) : (
                        <ul>
                            {await Promise.all(visibleDays.map(async (d) => {
                                const picks = await loadDaySummary(d, selectedMonthRevalidate);
                                const titles = picks.map((p, i) => ({round: i + 1, appId: p.appId, name: p.name}));
                                return (
                                    <li key={d} className='archive-list__toc'>
                                        <a href={`/review-guesser/archive/${d}`}>{d}</a>
                                        {titles.length > 0 && (
                                            <ol>
                                                {titles.map(t => (
                                                    <li key={`${d}-${t.appId}`}>
                                                        <a href={`/review-guesser/archive/${d}#round-${t.round}`}>{t.name}</a>
                                                    </li>
                                                ))}
                                            </ol>
                                        )}
                                    </li>
                                );
                            }))}
                        </ul>
                    )}
                </>
            )}
        </section>
    );
}

export const metadata: Metadata = {
    title: 'Archive',
    description: 'Browse previous daily challenges for Steam Review Guesser.',
    alternates: {
        canonical: '/review-guesser/archive',
    },
    keywords: [
        'Steam',
        'archive',
        'past challenges',
        'review guessing game'
    ],
    openGraph: {
        title: 'Archive',
        description: 'Browse previous daily challenges for Steam Review Guesser.',
        url: '/review-guesser/archive',
        images: ['/opengraph-image'],
    },
    twitter: {
        card: 'summary_large_image',
        title: 'Archive',
        description: 'Browse previous daily challenges for Steam Review Guesser.',
        images: ['/opengraph-image'],
    },
};


