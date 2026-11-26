import {Routes} from "../../app/routes";

/**
 * One list for the six leaderboard views. The toggle, the page header and any future
 * link source read this instead of repeating the hrefs — adding a view used to cost
 * three edits, and a missing header entry failed silently to the All-time copy.
 */
export type LeaderboardRoute = {
    href: string;
    label: string;
    title: string;
    subline: string;
};

export const LEADERBOARD_ROUTES: LeaderboardRoute[] = [
    {
        href: Routes.leaderboardToday,
        label: "Today",
        title: "Today",
        subline: "Today's total points by player",
    },
    {
        href: Routes.leaderboardWeekly,
        label: "Weekly",
        title: "Weekly",
        subline: "Last seven days",
    },
    {
        href: Routes.leaderboardSeason,
        label: "Season",
        title: "Season",
        subline: "Current season standings",
    },
    {
        href: Routes.leaderboard,
        label: "All-time",
        title: "All-time",
        subline: "Overall points summed across all days",
    },
    {
        href: Routes.leaderboardPerfectDays,
        label: "Perfect Days",
        title: "Perfect Days",
        subline: "Players who scored 25 out of 25 points in a single day",
    },
    {
        href: Routes.leaderboardHardest,
        label: "Hardest Games",
        title: "Hardest Games",
        subline: "Steam games players struggle with the most — ranked by lowest average score",
    },
];
