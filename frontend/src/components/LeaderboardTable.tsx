import "@/styles/components/leaderboard.css";
import Image from "next/image";

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
    avatarBlurdata?: string | null;
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
                {entries.map((entry, i) => (
                    <tr key={entry.steamId}>
                        <td>{i + 1}</td>
                        <td>
                            <div className="leaderboard__player">
                                {entry.avatar && (
                                    <div className="leaderboard__avatar-wrap"
                                         style={{backgroundImage: entry.avatarBlurdata ? `url(${entry.avatarBlurdata})` : undefined}}>
                                        <Image className="leaderboard__avatar"
                                               src={entry.avatar}
                                               placeholder={'empty'}
                                               alt=""
                                               width={24}
                                               height={24}/>
                                    </div>
                                )}
                                {entry.profileUrl ? (
                                    <a href={entry.profileUrl} target="_blank" rel="noopener noreferrer"
                                       className="leaderboard__profile-link">
                                        <strong>{entry.personaName || entry.steamId}</strong>
                                    </a>
                                ) : (
                                    <strong>{entry.personaName || entry.steamId}</strong>
                                )}
                            </div>
                        </td>
                        <td className="num">{entry.totalPoints}</td>
                        <td className="num">{entry.rounds}</td>
                        <td className="num">{entry.hits}</td>
                        <td className="num">{entry.tooHigh}</td>
                        <td className="num">{entry.tooLow}</td>
                        <td className="num">{entry.avgPoints.toFixed(2)}</td>
                    </tr>
                ))}
                </tbody>
            </table>
        </div>
    );
}


