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

export function formatPrice(amountCents: number, currency: string = 'USD', locale?: string): string {
    const amount = (amountCents ?? 0) / 100;
    try {
        return new Intl.NumberFormat(locale, {style: 'currency', currency, currencyDisplay: 'symbol'}).format(amount);
    } catch {
        return `${amount.toFixed(2)} ${currency}`;
    }
}


