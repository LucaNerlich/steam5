export const revalidate = 600;

async function loadDays(): Promise<string[]> {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    const res = await fetch(`${backend}/api/review-game/days?limit=120`, {
        headers: {'accept': 'application/json'},
        next: {revalidate: 600},
    });
    if (!res.ok) return [];
    return res.json();
}

export default async function ArchiveIndexPage() {
    const days = await loadDays();
    return (
        <section className="container">
            <h1>Archive</h1>
            {days.length === 0 ? (
                <p className="text-muted">No previous daily challenges found.</p>
            ) : (
                <ul>
                    {await Promise.all(days.map(async (d) => {
                        const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
                        const isToday = (() => {
                            try {
                                const now = new Date();
                                const todayStr = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate())).toISOString().slice(0, 10);
                                return d === todayStr;
                            } catch {
                                return false;
                            }
                        })();
                        const res = await fetch(`${backend}/api/review-game/day/${encodeURIComponent(d)}`, {
                            headers: {accept: 'application/json'},
                            next: {revalidate: isToday ? 60 : 31536000},
                        });
                        const data: { picks?: Array<{ appId: number; name: string }> } = res.ok ? await res.json() : {};
                        const picks = Array.isArray(data?.picks) ? data.picks : [];
                        const titles = picks.map((p, i) => ({round: i + 1, appId: p.appId, name: p.name}));
                        return (
                            <li key={d}>
                                <a href={`/review-guesser/archive/${d}`}>{d}</a>
                                {titles.length > 0 && (
                                    <ul>
                                        {titles.map(t => (
                                            <li key={`${d}-${t.appId}`}>
                                                <a href={`/review-guesser/archive/${d}#round-${t.round}`}>Round {t.round}: {t.name}</a>
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </li>
                        );
                    }))}
                </ul>
            )}
        </section>
    );
}


