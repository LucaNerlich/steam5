import {cache} from "react";

const getSeasonCountdown = cache(async () => {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";
    const res = await fetch(`${backend}/api/seasons/current`, {
        next: {revalidate: 900, tags: ["season-current"]}
    });
    if (!res.ok) {
        return null;
    }
    const data = await res.json() as {
        season: { endDate: string },
        daysRemaining: number
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
        <span className="footer__season-countdown">
            {label} · Ends {new Date(season.endDate).toLocaleDateString(undefined, {
            month: "short",
            day: "numeric"
        })}
        </span>
    );
}


