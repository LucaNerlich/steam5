type BreadcrumbItem = {
    name: string;
    url: string;
};

const defaultBase = (process.env.NEXT_PUBLIC_DOMAIN || "https://steam5.org").replace(/\/$/, "");

function normalizeUrl(url: string, base: string): string {
    if (/^https?:\/\//i.test(url)) {
        return url;
    }
    if (url.startsWith("/")) {
        return `${base}${url}`;
    }
    return `${base}/${url}`;
}

/**
 * Serializes a value as JSON that is safe to embed in an HTML `<script>` tag:
 * `<`, `>`, and `&` are escaped so data containing `</script>` cannot break out.
 */
export function serializeJsonLd(value: unknown): string {
    return JSON.stringify(value)
        .replace(/&/g, "\\u0026")
        .replace(/</g, "\\u003c")
        .replace(/>/g, "\\u003e");
}

export function buildBreadcrumbJsonLd(items: BreadcrumbItem[], base: string = defaultBase) {
    return {
        "@context": "https://schema.org",
        "@type": "BreadcrumbList",
        itemListElement: items.map((item, index) => ({
            "@type": "ListItem",
            position: index + 1,
            name: item.name,
            item: normalizeUrl(item.url, base),
        })),
    };
}
