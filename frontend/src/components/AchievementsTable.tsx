"use client";

import Avatar from "@/components/Avatar";
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
};

/**
 * Renders the achievements leaderboard as a responsive list of cards.
 *
 * @param achievements - Achievement records to display
 * @param serverOffsetMinutes - Server time offset used to calculate achievement metrics and titles
 * @param leaderboardEntries - Leaderboard players matched to achievement winners
 */
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
            <ul className="achievements-list" role="list">
                {Object.entries(ACHIEVEMENT_LABELS).map(([key, label]) => {
                    const achievement = achievements.find(a => a.userAchievement === key);
                    if (!achievement) return null;
                    const entry = entryBySteamId.get(achievement.steamId);
                    if (!entry) return null;

                    const metricText = achievementMetricText(key, achievement, serverOffsetMinutes);
                    const icon = ACHIEVEMENT_ICONS[key] ?? '';
                    const title = getAchievementTitle(key, achievement, serverOffsetMinutes);

                    return (
                        <li key={key} className="achievements-list__item">
                            <div className="achievements-list__icon" title={title}>{icon}</div>
                            <div className="achievements-list__content">
                                <span className="achievements-list__label">{label}</span>
                                <div className="achievements-list__winner">
                                    <Avatar src={entry.avatar} name={entry.personaName} size={24}/>
                                    <a
                                        href={`/profile/${encodeURIComponent(achievement.steamId)}`}
                                        className="leaderboard__profile-link"
                                        title={entry.personaName || achievement.steamId}
                                    >
                                        {entry.personaName || achievement.steamId}
                                    </a>
                                </div>
                            </div>
                            {metricText && (
                                <span className="achievements-list__metric">{metricText}</span>
                            )}
                        </li>
                    );
                })}
            </ul>
        </div>
    );
}
