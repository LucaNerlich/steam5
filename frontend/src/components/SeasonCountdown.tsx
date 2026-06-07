import {cache} from "react";
import Link from "next/link";
import {BACKEND_ORIGIN} from "@/lib/backend";
import {Routes} from "../../app/routes";

const getSeasonCountdown = cache(async () => {
    const res = await fetch(`${BACKEND_ORIGIN}/api/seasons/current`, {
        next: {revalidate: 900, tags: ["season-current"]}
    });
    if (!res.ok) return null;
    const data = await res.json() as {
        season: { seasonNumber: number; endDate: string };
        daysRemaining: number;
    };
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
            {label} · Ends {new Date(season.endDate).toLocaleDateString(undefined, {
                month: "short",
                day: "numeric",
            })}
        </Link>
    );
}


