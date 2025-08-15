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


