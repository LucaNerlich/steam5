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
            <table className="leaderboard__achievements-table">
                <thead>
                    <tr>
                        <th>Achievement</th>
                        <th>Winner</th>
                        <th className="num">Metric</th>
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
                                <td data-label="Achievement">
                                    <span className="leaderboard__achievement-label" title={title}>
                                        <span className="leaderboard__achievement-icon">{icon}</span>
                                        {label}
                                    </span>
                                    <span className="leaderboard__achievement-metric-mobile num">
                                        {metricText || '—'}
                                    </span>
                                </td>
                                <td data-label="Winner">
                                    <div className="leaderboard__achievement-winner">
                                        {entry.avatar && (
                                            <div
                                                className="leaderboard__achievement-avatar-wrap"
                                                style={{backgroundImage: entry.avatarBlurdata ? `url(${entry.avatarBlurdata})` : undefined}}
                                            >
                                                <Image
                                                    className="leaderboard__achievement-avatar"
                                                    src={entry.avatar}
                                                    placeholder="empty"
                                                    alt=""
                                                    width={20}
                                                    height={20}
                                                />
                                            </div>
                                        )}
                                        <a
                                            href={`/profile/${encodeURIComponent(achievement.steamId)}`}
                                            className="leaderboard__achievement-name"
                                        >
                                            {entry.personaName || achievement.steamId}
                                        </a>
                                    </div>
                                </td>
                                <td className="num leaderboard__achievement-metric-desktop" data-label="Metric">
                                    {metricText || '—'}
                                </td>
                            </tr>
                        );
                    })}
                </tbody>
            </table>
        </div>
    );
}
