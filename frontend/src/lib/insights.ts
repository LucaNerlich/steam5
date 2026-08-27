export type InsightType =
    | "DAY_STREAK"
    | "BEST_DAY_EVER"
    | "BEAT_THE_ODDS"
    | "WELCOME_BACK"
    | "MOST_IMPROVED"
    | "WEEKLY_ACHIEVEMENT"
    | "HOT_STREAK"
    | "TOP_COMMENT"
    | "MILESTONE";

export const INSIGHT_EMOJI: Record<InsightType, string> = {
    DAY_STREAK: "🔥",
    BEST_DAY_EVER: "🏆",
    BEAT_THE_ODDS: "🎯",
    WELCOME_BACK: "👋",
    MOST_IMPROVED: "📊",
    WEEKLY_ACHIEVEMENT: "🏅",
    HOT_STREAK: "📈",
    TOP_COMMENT: "💬",
    MILESTONE: "⭐",
};
