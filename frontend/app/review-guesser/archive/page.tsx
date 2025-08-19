async function loadDays(): Promise<string[]> {
    const base = process.env.NEXT_PUBLIC_DOMAIN || 'http://localhost:3000';
    const res = await fetch(`${base}/api/review-game/days?limit=120`, {
        cache: 'no-store',
        headers: {'accept': 'application/json'}
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
                        const base = process.env.NEXT_PUBLIC_DOMAIN || 'http://localhost:3000';
                        const res = await fetch(`${base}/api/review-game/day/summary/${encodeURIComponent(d)}`, {cache: 'no-store'});
                        const titles: Array<{
                            round: number;
                            appId: number;
                            name: string
                        }> = res.ok ? await res.json() : [];
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


