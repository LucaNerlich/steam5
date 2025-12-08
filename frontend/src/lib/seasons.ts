import type {AwardView} from "@/types/seasons";

export type AwardGroup = {
    category: string;
    label: string;
    awards: AwardView[];
};

const numberFormatter = new Intl.NumberFormat();

export function groupAwardsByCategory(awards: AwardView[]): AwardGroup[] {
    if (!Array.isArray(awards) || awards.length === 0) {
        return [];
    }
    const grouped: Record<string, AwardGroup> = {};
    for (const award of awards) {
        if (!grouped[award.category]) {
            grouped[award.category] = {
                category: award.category,
                label: award.categoryLabel,
                awards: []
            };
        }
        grouped[award.category].awards.push(award);
    }
    return Object.values(grouped);
}

export function formatAwardMetric(award: AwardView): string {
    switch (award.category) {
        case "MOST_POINTS":
            return `${numberFormatter.format(award.metricValue)} pts`;
        case "MOST_HITS":
            return `${numberFormatter.format(award.metricValue)} hits`;
        case "HIGHEST_AVG_POINTS_PER_DAY":
            return `${(award.metricValue / 100).toFixed(2)} avg pts/day`;
        case "LONGEST_STREAK":
            return `${award.metricValue} ${award.metricValue === 1 ? "day" : "days"}`;
        default:
            return numberFormatter.format(award.metricValue);
    }
}

export function rankClassName(level: number): string {
    if (level === 1) return "season-card__rank season-card__rank--gold";
    if (level === 2) return "season-card__rank season-card__rank--silver";
    if (level === 3) return "season-card__rank season-card__rank--bronze";
    return "season-card__rank";
}

