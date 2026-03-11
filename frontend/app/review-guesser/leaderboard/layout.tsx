import {ReactNode} from "react";
import "@/styles/components/leaderboard.css";
import LeaderboardToggle from "@/components/LeaderboardToggle";

export default function LeaderboardLayout({children}: { children: ReactNode }) {
    return (
        <section className="container">
            <h1>Leaderboard</h1>
            <LeaderboardToggle />
            {children}
        </section>
    );
}
