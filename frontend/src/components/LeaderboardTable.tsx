import "@/styles/components/leaderboard.css";

export type LeaderEntry = {
    steamId: string;
    personaName: string;
    totalPoints: number;
    rounds: number;
    hits: number;
    tooHigh: number;
    tooLow: number;
    avgPoints: number;
    avatar?: string | null;
    profileUrl?: string | null;
};

export default function LeaderboardTable({entries, ariaLabel}: { entries: LeaderEntry[]; ariaLabel: string }) {
    return (
        <div className="leaderboard">
            <table className="leaderboard__table" aria-label={ariaLabel}>
                <thead>
                <tr>
                    <th scope="col" className="num">#</th>
                    <th scope="col">Player</th>
                    <th scope="col" className="num">Points</th>
                    <th scope="col" className="num">Rounds</th>
                    <th scope="col" className="num">Hits</th>
                    <th scope="col" className="num">Too High</th>
                    <th scope="col" className="num">Too Low</th>
                    <th scope="col" className="num">Avg</th>
                </tr>
                </thead>
                <tbody>
                {entries.map((e, i) => (
                    <tr key={e.steamId}>
                        <td>{i + 1}</td>
                        <td>
                            <div className="leaderboard__player">
                                {e.avatar && (
                                    <img className="leaderboard__avatar" src={e.avatar} alt="" width={20} height={20}/>
                                )}
                                {e.profileUrl ? (
                                    <a href={e.profileUrl} target="_blank" rel="noopener noreferrer"
                                       className="leaderboard__profile-link">
                                        <strong>{e.personaName || e.steamId}</strong>
                                    </a>
                                ) : (
                                    <strong>{e.personaName || e.steamId}</strong>
                                )}
                            </div>
                        </td>
                        <td className="num">{e.totalPoints}</td>
                        <td className="num">{e.rounds}</td>
                        <td className="num">{e.hits}</td>
                        <td className="num">{e.tooHigh}</td>
                        <td className="num">{e.tooLow}</td>
                        <td className="num">{e.avgPoints.toFixed(2)}</td>
                    </tr>
                ))}
                </tbody>
            </table>
        </div>
    );
}


