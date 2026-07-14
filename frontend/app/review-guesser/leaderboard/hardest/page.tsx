import type {Metadata} from "next";
import HardestGamesTable from "@/components/HardestGamesTable";
import {buildBreadcrumbJsonLd} from "@/lib/seo";
import {fetchHardestGames} from "@/lib/hardestGames";
import {Routes} from "../../../routes";

export const revalidate = 3600;

export default async function LeaderboardHardestPage() {
    const games = await fetchHardestGames();
    const breadcrumbJsonLd = buildBreadcrumbJsonLd([
        {name: "Home", url: Routes.home},
        {name: "Leaderboard", url: Routes.leaderboard},
        {name: "Hardest", url: Routes.leaderboardHardest},
    ]);

    return (
        <>
            <script type="application/ld+json" dangerouslySetInnerHTML={{
                __html: JSON.stringify(breadcrumbJsonLd)
            }}/>
            <HardestGamesTable initialData={games}/>
        </>
    );
}

export const metadata: Metadata = {
    title: "Leaderboard — Hardest Games",
    description: "The Steam games players struggle with the most, ranked by lowest average score, with deception metrics and most-missed review buckets.",
    alternates: {
        canonical: Routes.leaderboardHardest
    },
    keywords: [
        "hardest games",
        "Steam games",
        "deception",
        "review guessing",
        "statistics",
        "difficulty",
        "leaderboard"
    ],
    openGraph: {
        title: "Leaderboard — Hardest Games",
        description: "The Steam games players struggle with the most, with deception metrics.",
        url: Routes.leaderboardHardest,
        images: ["/opengraph-image"]
    },
    twitter: {
        card: "summary_large_image",
        title: "Leaderboard — Hardest Games",
        description: "The Steam games players struggle with the most, with deception metrics.",
        images: ["/opengraph-image"]
    }
};
