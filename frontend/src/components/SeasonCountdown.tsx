import {cache} from "react";
import Link from "next/link";
import {fetchBackendJson} from "@/lib/backend";
import {Routes} from "../../app/routes";

type SeasonCountdownData = {
    season: { seasonNumber: number; endDate: string };
    daysRemaining: number;
};

const getSeasonCountdown = cache(async () => {
    // fetchBackendJson resolves null on any failure: this is a footer line, and a backend
    // outage must not throw out of the root layout (which has no global-error boundary).
    const {data} = await fetchBackendJson<SeasonCountdownData>("/api/seasons/current", {
        revalidate: 900,
        tags: ["season-current"],
    });
    return data;
});

export default async function SeasonCountdown() {
    const data = await getSeasonCountdown();
    if (!data) return null;
    const {season, daysRemaining} = data;
    const label = daysRemaining === 0 ? "Season ends today" :
        daysRemaining === 1 ? "1 day left in season" :
            `${daysRemaining} days left in season`;

    return (
        <Link href={Routes.seasonDetail(season.seasonNumber)} className="footer__season-countdown">
            {label} · Ends {new Date(season.endDate).toLocaleDateString('en-US', {
                month: "short",
                day: "numeric",
            })}
        </Link>
    );
}


