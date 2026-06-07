"use client";

import Image from "next/image";
import {
    ACHIEVEMENT_LABELS,
    ACHIEVEMENT_ICONS,
    UserAchievement,
    achievementMetricText,
    getAchievementTitle,
} from "@/lib/achievements";

type LeaderEntry = {
    steamId: string;
    personaName: string;
    avatar?: string | null;
    avatarBlurdata?: string | null;
};

export default function AchievementsTable({
    achievements,
    serverOffsetMinutes,
    leaderboardEntries,
}: {
    achievements: UserAchievement[];
    serverOffsetMinutes: number;
    leaderboardEntries: LeaderEntry[];
}) {
    if (!achievements || achievements.length === 0) return null;

    const entryBySteamId = new Map(leaderboardEntries.map(e => [e.steamId, e]));

    return (
        <div className="leaderboard__achievements">
            <h3 className="leaderboard__achievements-title">Achievements</h3>
            <div className="leaderboard__scroll">
                <table className="leaderboard__table" aria-label="Achievements">
                    <thead>
                        <tr>
                            <th scope="col">Achievement</th>
                            <th scope="col">Winner</th>
                            <th scope="col" className="num">Metric</th>
                        </tr>
                    </thead>
                    <tbody>
                        {Object.entries(ACHIEVEMENT_LABELS).map(([key, label]) => {
                            const achievement = achievements.find(a => a.userAchievement === key);
                            if (!achievement) return null;
                            const entry = entryBySteamId.get(achievement.steamId);
                            if (!entry) return null;

                            const metricText = achievementMetricText(key, achievement, serverOffsetMinutes);
                            const icon = ACHIEVEMENT_ICONS[key] ?? '';
                            const title = getAchievementTitle(key, achievement, serverOffsetMinutes);

                            return (
                                <tr key={key}>
                                    <td>
                                        <span className="leaderboard__achievement-label" title={title}>
                                            <span className="leaderboard__achievement-icon">{icon}</span>
                                            {label}
                                        </span>
                                        <span className="leaderboard__achievement-metric-mobile num">
                                            {metricText || '—'}
                                        </span>
                                    </td>
                                    <td>
                                        <div className="leaderboard__player">
                                            {entry.avatar && (
                                                <div
                                                    className="leaderboard__avatar-wrap"
                                                    style={{backgroundImage: entry.avatarBlurdata ? `url(${entry.avatarBlurdata})` : undefined}}
                                                >
                                                    <Image
                                                        className="leaderboard__avatar"
                                                        src={entry.avatar}
                                                        placeholder="empty"
                                                        alt=""
                                                        width={24}
                                                        height={24}
                                                    />
                                                </div>
                                            )}
                                            <a
                                                href={`/profile/${encodeURIComponent(achievement.steamId)}`}
                                                className="leaderboard__profile-link"
                                            >
                                                {entry.personaName || achievement.steamId}
                                            </a>
                                        </div>
                                    </td>
                                    <td className="num leaderboard__achievement-metric-desktop">
                                        {metricText || '—'}
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
