import "@/styles/components/leaderboard.css";

export default function LeaderboardSkeleton(props: {
    variant?: "full" | "table";
    rows?: number;
}) {
    const variant = props.variant ?? "table";
    const rows = props.rows ?? 10;

    return (
        <div className={`leaderboard-skeleton${variant === "full" ? " leaderboard-skeleton--full" : ""}`} role="status" aria-label="Loading leaderboard">
            {variant === "full" && (
                <div className="leaderboard-skeleton__heading" aria-hidden="true">
                    <div className="leaderboard-skeleton__title"/>
                    <div className="leaderboard-skeleton__tabs">
                        <div className="leaderboard-skeleton__tab"/>
                        <div className="leaderboard-skeleton__tab"/>
                        <div className="leaderboard-skeleton__tab"/>
                        <div className="leaderboard-skeleton__tab"/>
                    </div>
                    <div className="leaderboard-skeleton__subtitle"/>
                    <div className="leaderboard-skeleton__subline"/>
                </div>
            )}

            <div className="leaderboard-skeleton__table" aria-hidden="true">
                <div className="leaderboard-skeleton__row leaderboard-skeleton__row--header">
                    <span className="leaderboard-skeleton__cell leaderboard-skeleton__cell--rank"/>
                    <span className="leaderboard-skeleton__cell leaderboard-skeleton__cell--player"/>
                    <span className="leaderboard-skeleton__cell leaderboard-skeleton__cell--num"/>
                    <span className="leaderboard-skeleton__cell leaderboard-skeleton__cell--num"/>
                    <span className="leaderboard-skeleton__cell leaderboard-skeleton__cell--num"/>
                    <span className="leaderboard-skeleton__cell leaderboard-skeleton__cell--num"/>
                    <span className="leaderboard-skeleton__cell leaderboard-skeleton__cell--num leaderboard-skeleton__mobile-hide"/>
                    <span className="leaderboard-skeleton__cell leaderboard-skeleton__cell--num leaderboard-skeleton__mobile-hide"/>
                    <span className="leaderboard-skeleton__cell leaderboard-skeleton__cell--num leaderboard-skeleton__mobile-hide"/>
                    <span className="leaderboard-skeleton__cell leaderboard-skeleton__cell--num"/>
                </div>

                {Array.from({length: rows}).map((_, idx) => (
                    <div key={idx} className="leaderboard-skeleton__row">
                        <span className="leaderboard-skeleton__cell leaderboard-skeleton__cell--rank"/>
                        <span className="leaderboard-skeleton__player-cell">
                            <span className="leaderboard-skeleton__avatar"/>
                            <span className="leaderboard-skeleton__cell leaderboard-skeleton__cell--player"/>
                        </span>
                        <span className="leaderboard-skeleton__cell leaderboard-skeleton__cell--num"/>
                        <span className="leaderboard-skeleton__cell leaderboard-skeleton__cell--num"/>
                        <span className="leaderboard-skeleton__cell leaderboard-skeleton__cell--num"/>
                        <span className="leaderboard-skeleton__cell leaderboard-skeleton__cell--num"/>
                        <span className="leaderboard-skeleton__cell leaderboard-skeleton__cell--num leaderboard-skeleton__mobile-hide"/>
                        <span className="leaderboard-skeleton__cell leaderboard-skeleton__cell--num leaderboard-skeleton__mobile-hide"/>
                        <span className="leaderboard-skeleton__cell leaderboard-skeleton__cell--num leaderboard-skeleton__mobile-hide"/>
                        <span className="leaderboard-skeleton__cell leaderboard-skeleton__cell--num"/>
                    </div>
                ))}
            </div>

            <div className="leaderboard-skeleton__avg" aria-hidden="true"/>
        </div>
    );
}
