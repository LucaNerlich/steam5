/**
 * Returns an ordinal string for a number, e.g. 1 → "1st", 11 → "11th", 22 → "22nd".
 * Handles the 11th/12th/13th edge cases correctly.
 */
export function ordinal(value: number): string {
    const mod100 = value % 100;
    if (mod100 >= 11 && mod100 <= 13) return `${value}th`;
    switch (value % 10) {
        case 1: return `${value}st`;
        case 2: return `${value}nd`;
        case 3: return `${value}rd`;
        default: return `${value}th`;
    }
}

/**
 * Maps a placement level to its tier name. The caller is responsible for
 * composing the tier into a CSS class string.
 */
export function placementTier(level: number): 'gold' | 'silver' | 'bronze' | 'neutral' {
    if (level === 1) return 'gold';
    if (level === 2) return 'silver';
    if (level === 3) return 'bronze';
    return 'neutral';
}

export function formatDate(date: string | Date, locale?: string): string {
    const d = typeof date === 'string' ? new Date(date) : date;
    return d.toLocaleDateString(locale, {year: 'numeric', month: 'short', day: 'numeric'});
}

/**
 * Compact relative timestamp for comment lists (e.g. "just now", "5m", "3h", "2d").
 * Falls back to a short date for anything older than a week.
 */
export function formatRelativeTime(
    date: string | Date,
    now: Date = new Date(),
    locale?: string,
): string {
    const d = typeof date === 'string' ? new Date(date) : date;
    if (Number.isNaN(d.getTime())) return '';

    const diffMs = now.getTime() - d.getTime();
    if (diffMs < 0) return formatDate(d, locale);

    const seconds = Math.floor(diffMs / 1000);
    if (seconds < 45) return 'just now';
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes}m`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h`;
    const days = Math.floor(hours / 24);
    if (days < 7) return `${days}d`;
    return formatDate(d, locale);
}

export function formatPrice(amountCents: number, currency: string = 'USD', locale?: string): string {
    const amount = (amountCents ?? 0) / 100;
    try {
        return new Intl.NumberFormat(locale, {style: 'currency', currency, currencyDisplay: 'symbol'}).format(amount);
    } catch {
        return `${amount.toFixed(2)} ${currency}`;
    }
}


