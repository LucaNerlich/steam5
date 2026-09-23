import type {Metadata} from "next";
import type {ReviewGameState} from "@/types/review-game";
import ReviewGuesserHero from "@/components/ReviewGuesserHero";
import ArchiveOfflineRound from "@/components/ArchiveOfflineRound";
import ArchiveResetForDay from "@/components/ArchiveResetForDay";
import ArchiveSummary from "@/components/ArchiveSummary";
import DayComments from "@/components/DayComments";
import {notFound} from "next/navigation";
import {formatDate} from "@/lib/format";
import {buildBreadcrumbJsonLd, serializeJsonLd} from "@/lib/seo";
import "@/styles/components/archive.css";
import GameInfoSection from "@/components/GameInfoSection";
import React from "react";
import {Routes} from "../../../routes";
import {BACKEND_ORIGIN as backend} from "@/lib/backend";

export const revalidate = 31536000;

async function loadArchived(date: string): Promise<ReviewGameState | null> {
    try {
        const res = await fetch(`${backend}/api/review-game/day/${encodeURIComponent(date)}`, {
            headers: {"accept": "application/json"},
            next: {revalidate: 31536000},
        });
        if (!res.ok) return null;
        return res.json();
    } catch {
        return null;
    }
}

type DayAnswer = { appId: number; totalReviews: number; actualBucket: string };

async function loadAnswers(date: string): Promise<Record<number, {
    actualBucket: string;
    totalReviews: number
}>> {
    // Dedicated read-only endpoint that serves the finished day's answers.
    // Restricted server-side to days before today, so the archive can never
    // expose a live day's results.
    try {
        const res = await fetch(`${backend}/api/review-game/day/${encodeURIComponent(date)}/answers`, {
            headers: {'accept': 'application/json'},
            next: {revalidate: 31536000},
        });
        if (!res.ok) return {};
        const rows: unknown = await res.json();
        if (!Array.isArray(rows)) return {};
        const out: Record<number, { actualBucket: string; totalReviews: number }> = {};
        for (const row of rows as DayAnswer[]) {
            if (typeof row?.appId === 'number'
                && typeof row.actualBucket === 'string'
                && typeof row.totalReviews === 'number') {
                out[row.appId] = {actualBucket: row.actualBucket, totalReviews: row.totalReviews};
            }
        }
        return out;
    } catch {
        return {};
    }
}

export default async function ArchivePage({params}: { params: Promise<{ date: string }> }) {
    const {date} = await params;
    // The archive is for finished days only; the index never links today, and
    // this rejects direct hits so a live day can never be served here.
    if (date >= new Date().toISOString().slice(0, 10)) {
        notFound();
    }
    const data = await loadArchived(date);
    const breadcrumbJsonLd = buildBreadcrumbJsonLd([
        {name: "Home", url: Routes.home},
        {name: "Archive", url: Routes.archive},
        {name: formatDate(date), url: `${Routes.archive}/${encodeURIComponent(date)}`},
    ]);
    if (!data || !data.picks || data.picks.length === 0) {
        notFound();
    }

    const answers = await loadAnswers(date);

    return (
        <section className="container">
            <script type="application/ld+json" dangerouslySetInnerHTML={{
                __html: serializeJsonLd(breadcrumbJsonLd)
            }} />
            <h1>Archive — {formatDate(date)}</h1>
            <DayComments gameDate={date} readOnly/>
            <ArchiveSummary
                date={date}
                bucketLabels={data.buckets}
                bucketTitles={data.bucketTitles || data.buckets}
            />
            <nav aria-label="Rounds table of contents" className="archive__toc">
                <ol>
                    {data.picks.map((app, idx) => (
                        <li key={app.appId}><a href={`#round-${idx + 1}`}>{app.name}</a></li>
                    ))}
                </ol>
            </nav>
            {data.picks.map((pick, idx) => (
                <div key={pick.appId} id={`round-${idx + 1}`} className="archive__round">
                    <ReviewGuesserHero today={data} pick={pick} roundIndex={idx + 1}/>
                    <ArchiveOfflineRound
                        appId={pick.appId}
                        buckets={data.buckets}
                        bucketTitles={data.bucketTitles}
                        roundIndex={idx + 1}
                        totalRounds={data.picks.length}
                        pickName={pick.name}
                        gameDate={date}
                        offlineAnswer={answers[pick.appId] ?? null}
                    />
                    <GameInfoSection pick={pick}/>
                </div>
            ))}
            <ArchiveResetForDay date={date}/>
        </section>
    );
}

export async function generateMetadata({params}: { params: Promise<{ date: string }> }): Promise<Metadata> {
    const {date} = await params;
    const title = `Archive — ${formatDate(date)}`;
    const description = `Past daily challenge for ${formatDate(date)} — Steam Review Guesser.`;
    const base = (process.env.NEXT_PUBLIC_DOMAIN || 'https://steam5.org').replace(/\/$/, '');
    let ogUrl = '/opengraph-image';
    try {
        const day: ReviewGameState | null = await fetch(`${backend}/api/review-game/day/${encodeURIComponent(date)}`, {
            headers: { 'accept': 'application/json' },
            next: { revalidate: 31536000 }
        }).then(r => r.ok ? r.json() : null);
        const firstShot = day?.picks?.[0]?.screenshots?.[0];
        const rawImg = firstShot?.pathFull || firstShot?.pathThumbnail;
        if (rawImg) {
            try { ogUrl = new URL(rawImg).toString(); }
            catch { ogUrl = new URL(rawImg.startsWith('/') ? rawImg : `/${rawImg}`, base).toString(); }
        }
    } catch { /* ignore, fallback to default */ }
    return {
        title,
        description,
        keywords: [
            'Steam',
            'archive',
            'past challenges',
            'review guessing game',
            date
        ],
        alternates: {
            canonical: `/review-guesser/archive/${encodeURIComponent(date)}`,
        },
        openGraph: {
            title,
            description,
            url: `/review-guesser/archive/${encodeURIComponent(date)}`,
            images: [ogUrl],
        },
        twitter: {
            card: 'summary_large_image',
            title,
            description,
            images: [ogUrl],
        },
    };
}


