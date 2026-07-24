import type {Metadata} from "next";
import PerfectDaysTable from "@/components/PerfectDaysTable";
import {buildBreadcrumbJsonLd} from "@/lib/seo";
import {fetchPerfectDays} from "@/lib/perfectDays";
import {Routes} from "../../../routes";

export const revalidate = 3600;

export default async function PerfectDaysPage() {
    const days = await fetchPerfectDays();
    const breadcrumbJsonLd = buildBreadcrumbJsonLd([
        {name: "Home", url: Routes.home},
        {name: "Leaderboard", url: Routes.leaderboard},
        {name: "Perfect Days", url: Routes.leaderboardPerfectDays},
    ]);

    return (
        <>
            <script type="application/ld+json" dangerouslySetInnerHTML={{
                __html: JSON.stringify(breadcrumbJsonLd)
            }}/>
            <PerfectDaysTable initialData={days}/>
        </>
    );
}

export const metadata: Metadata = {
    title: "Leaderboard — Perfect Days",
    description: "Players who scored 25 out of 25 points in a single day, with dates, games, and links to the archive.",
    alternates: {
        canonical: Routes.leaderboardPerfectDays
    },
    keywords: [
        "perfect day",
        "25 points",
        "Steam",
        "review guessing",
        "leaderboard",
        "flawless"
    ],
    openGraph: {
        title: "Leaderboard — Perfect Days",
        description: "Players who scored 25 out of 25 points in a single day.",
        url: Routes.leaderboardPerfectDays,
        images: ["/opengraph-image"]
    },
    twitter: {
        card: "summary_large_image",
        title: "Leaderboard — Perfect Days",
        description: "Players who scored 25 out of 25 points in a single day.",
        images: ["/opengraph-image"]
    }
};
