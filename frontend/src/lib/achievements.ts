/**
 * Achievement display metadata and formatting utilities.
 * Single source of truth for the frontend side of the achievement system.
 * Used by both LeaderboardTable (inline badge tooltips) and AchievementsTable
 * (the standalone achievements section).
 */

export type UserAchievement = {
    steamId: string;
    userAchievement: string;
    avgMinutes?: number;
    avgPoints?: number;
    perfectRounds?: number;
    perfectDays?: number;
    totalSeconds?: number;
};

export const ACHIEVEMENT_LABELS: Record<string, string> = {
    EARLY_BIRD: 'Early Bird',
    NIGHT_OWL: 'Night Owl',
    SHARPSHOOTER: 'Sharpshooter',
    BULLSEYE: 'Bullseye',
    PERFECT_DAY: 'Perfect Day',
    CHEETAH: 'Cheetah',
    SLOTH: 'Sloth',
};

export const ACHIEVEMENT_TITLES: Record<string, string> = {
    EARLY_BIRD: 'Plays the earliest ☀',
    NIGHT_OWL: 'Plays the latest 🌙',
    SHARPSHOOTER: 'Highest average points per guess 🔫',
    BULLSEYE: 'Most perfect rounds (points = 5) 🎯',
    PERFECT_DAY: 'Most days with perfect total points 🎩',
    CHEETAH: 'Least time between first and last guess per day 🐆',
    SLOTH: 'Most time between first and last guess per day 🦥',
};

export const ACHIEVEMENT_ICONS: Record<string, string> = {
    EARLY_BIRD: '☀',
    NIGHT_OWL: '🌙',
    SHARPSHOOTER: '🔫',
    BULLSEYE: '🎯',
    PERFECT_DAY: '🎩',
    CHEETAH: '🐆',
    SLOTH: '🦥',
};

export function getAchievementLabel(key: string | null | undefined): string | null {
    if (!key) return null;
    return ACHIEVEMENT_LABELS[key] ?? key.split('_').map(s => s.charAt(0) + s.slice(1).toLowerCase()).join(' ');
}

export function formatDuration(seconds: number): string {
    if (seconds < 60) return `${seconds}s`;
    const minutes = Math.floor(seconds / 60);
    const secs = seconds % 60;
    if (minutes < 60) return `${minutes}m ${secs}s`;
    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;
    return `${hours}h ${mins}m`;
}

export function formatTimeOfDay(minutesSinceMidnightServer: number, serverOffsetMinutes = 0): string {
    const utcMinutesSinceMidnight = minutesSinceMidnightServer - serverOffsetMinutes;
    const now = new Date();
    const utcDate = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate(), 0, 0, 0, 0));
    const utcTime = new Date(utcDate.getTime() + utcMinutesSinceMidnight * 60000);
    const localHours = utcTime.getHours();
    const localMins = utcTime.getMinutes();
    const period = localHours >= 12 ? 'PM' : 'AM';
    const displayHours = localHours === 0 ? 12 : localHours > 12 ? localHours - 12 : localHours;
    return `${displayHours}:${localMins.toString().padStart(2, '0')} ${period}`;
}

export function getAchievementTitle(
    key: string | null | undefined,
    achievement?: UserAchievement,
    serverOffsetMinutes = 0,
): string | undefined {
    if (!key) return undefined;
    const baseTitle = ACHIEVEMENT_TITLES[key];
    if (!achievement) return baseTitle;
    switch (key) {
        case 'EARLY_BIRD':
        case 'NIGHT_OWL':
            return achievement.avgMinutes != null
                ? `${baseTitle} (avg: ${formatTimeOfDay(achievement.avgMinutes, serverOffsetMinutes)})`
                : baseTitle;
        case 'SHARPSHOOTER':
            return achievement.avgPoints != null
                ? `${baseTitle} (${achievement.avgPoints.toFixed(2)} pts/guess)`
                : baseTitle;
        case 'BULLSEYE':
            return achievement.perfectRounds != null
                ? `${baseTitle} (${achievement.perfectRounds} perfect rounds)`
                : baseTitle;
        case 'PERFECT_DAY':
            return achievement.perfectDays != null
                ? `${baseTitle} (${achievement.perfectDays} perfect days)`
                : baseTitle;
        case 'CHEETAH':
        case 'SLOTH':
            return achievement.totalSeconds != null
                ? `${baseTitle} (total: ${formatDuration(achievement.totalSeconds)})`
                : baseTitle;
        default:
            return baseTitle;
    }
}

export function achievementMetricText(key: string, achievement: UserAchievement, serverOffsetMinutes: number): string | null {
    switch (key) {
        case 'EARLY_BIRD':
        case 'NIGHT_OWL':
            if (achievement.avgMinutes != null) {
                try { return formatTimeOfDay(achievement.avgMinutes, serverOffsetMinutes); }
                catch { return `${Math.round(achievement.avgMinutes)} min`; }
            }
            return null;
        case 'SHARPSHOOTER':
            return achievement.avgPoints != null ? `${achievement.avgPoints.toFixed(2)} pts/guess` : null;
        case 'BULLSEYE':
            return achievement.perfectRounds != null ? `${achievement.perfectRounds} rounds` : null;
        case 'PERFECT_DAY':
            return achievement.perfectDays != null ? `${achievement.perfectDays} days` : null;
        case 'CHEETAH':
        case 'SLOTH':
            return achievement.totalSeconds != null ? formatDuration(achievement.totalSeconds) : null;
        default:
            return null;
    }
}
