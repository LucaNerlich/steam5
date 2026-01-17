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
