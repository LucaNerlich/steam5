import {ImageResponse} from "next/og";

export const runtime = "edge";
export const size = {width: 1200, height: 630};
export const contentType = "image/png";

function arrayBufferToBase64(buffer: ArrayBuffer): string {
    let binary = "";
    const bytes = new Uint8Array(buffer);
    const len = bytes.byteLength;
    for (let i = 0; i < len; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    // Edge runtime provides btoa
    return btoa(binary);
}

export default async function Image(req: Request) {
    const url = new URL(req.url);
    const variant = (url.searchParams.get("variant") || "profile").toLowerCase();
    const steamId = url.searchParams.get("steamId");
    const gameDate = url.searchParams.get("gameDate");

    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

    // Fonts (cached)
    const [fontRegular, fontBold] = await Promise.all([
        fetch("https://og-playground.vercel.app/font/Inter-Regular.ttf", { next: { revalidate: 86400 } }).then(r => r.arrayBuffer()).catch(() => null),
        fetch("https://og-playground.vercel.app/font/Inter-Bold.ttf", { next: { revalidate: 86400 } }).then(r => r.arrayBuffer()).catch(() => null),
    ]);

    const bg = "#0b1220";
    const panelBg = "#0f172a";
    const panelBorder = "#1f2937";

    function pickAccent(str: string | null | undefined): string {
        if (!str) return "#6366f1";
        let h = 0;
        for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) >>> 0;
        const hue = h % 360;
        return `hsl(${hue} 80% 60%)`;
    }

    // Default icon for fallback
    const iconArrayBuffer = await fetch(new URL("../public/icon-full.svg", import.meta.url)).then(r => r.arrayBuffer());
    const iconDataUri = `data:image/svg+xml;base64,${arrayBufferToBase64(iconArrayBuffer)}`;

    type ProfileResponse = {
        steamId: string;
        personaName?: string | null;
        avatar?: string | null;
        avatarBlurdata?: string | null;
        profileUrl?: string | null;
        stats: { totalPoints: number; rounds: number; hits: number; tooHigh: number; tooLow: number; avgPoints: number };
        days: Array<{ date: string; rounds: Array<{ points: number; selectedBucket: string; actualBucket: string }> }>;
    };

    type Leader = { steamId: string; personaName?: string; totalPoints: number; rounds: number; hits: number; tooHigh: number; tooLow: number; avgPoints: number };

    async function fetchProfile(id: string): Promise<ProfileResponse | null> {
        try {
            const res = await fetch(`${backend}/api/profile/${encodeURIComponent(id)}`, { next: { revalidate: 300 } });
            if (!res.ok) return null;
            return await res.json();
        } catch { return null; }
    }

    async function fetchLeaders(): Promise<Leader[] | null> {
        try {
            const res = await fetch(`${backend}/api/leaderboard`, { next: { revalidate: 300 } });
            if (!res.ok) return null;
            return await res.json();
        } catch { return null; }
    }

    function percentileRank(value: number, values: number[]): number {
        if (values.length === 0) return 100;
        let below = 0;
        for (const v of values) if (v <= value) below++;
        const pct = 100 - Math.floor((below / values.length) * 100);
        return Math.max(1, Math.min(99, pct));
    }

    function rankBadge(pctTop: number): { label: string; color: string } {
        if (pctTop <= 1) return { label: "S+", color: "#22c55e" };
        if (pctTop <= 5) return { label: "S", color: "#10b981" };
        if (pctTop <= 10) return { label: "A", color: "#34d399" };
        if (pctTop <= 25) return { label: "B", color: "#fde047" };
        if (pctTop <= 50) return { label: "C", color: "#f59e0b" };
        return { label: "Rookie", color: "#ef4444" };
    }

    // Prepare data depending on variant
    let title = "Steam5";
    let subtitle = "Review Guesser";
    let avatarSrc: string | null = null;
    let primary = "#6366f1";
    let statsLines: Array<{ label: string; value: string }> = [];
    let kdaText: string | null = null;
    let badge: { label: string; color: string } | null = null;

    if (variant === "profile" && steamId) {
        const [profile, leaders] = await Promise.all([fetchProfile(steamId), fetchLeaders()]);
        if (profile) {
            const name = profile.personaName?.trim() || profile.steamId;
            title = name;
            subtitle = "Profile — Review Guesser";
            avatarSrc = profile.avatar || null;
            primary = pickAccent(profile.steamId);
            const totalPct = leaders ? percentileRank(profile.stats.totalPoints, leaders.map(l => l.totalPoints)) : 100;
            const avgPct = leaders ? percentileRank(profile.stats.avgPoints, leaders.map(l => l.avgPoints)) : 100;
            badge = rankBadge(totalPct);
            statsLines = [
                { label: "Total Points", value: String(profile.stats.totalPoints) },
                { label: "Avg Points", value: profile.stats.avgPoints.toFixed(2) + ` · Top ${avgPct}%` },
                { label: "Rounds", value: String(profile.stats.rounds) },
                { label: "Top Rank", value: `Top ${totalPct}%` },
            ];
            const recent = profile.days.flatMap(d => d.rounds).slice(-10);
            const hits = recent.filter(r => r.selectedBucket === r.actualBucket).length;
            const misses = recent.length - hits;
            const tooHigh = recent.filter(r => r.selectedBucket > r.actualBucket).length;
            const tooLow = recent.filter(r => r.selectedBucket < r.actualBucket).length;
            kdaText = recent.length > 0 ? `${hits} / ${misses} / ${Math.max(0, recent.length - hits - misses)}` : null;
            // Show explicit breakdown
            statsLines.push({ label: "Recent H/H/L", value: `${hits}/${tooHigh}/${tooLow}` });
        }
    } else if (variant === "match" && steamId && gameDate) {
        const profile = await fetchProfile(steamId);
        if (profile) {
            const day = profile.days.find(d => d.date === gameDate);
            const name = profile.personaName?.trim() || profile.steamId;
            title = `${name} — ${gameDate}`;
            subtitle = "Match Share";
            avatarSrc = profile.avatar || null;
            primary = pickAccent(profile.steamId);
            if (day) {
                const total = day.rounds.reduce((a, r) => a + r.points, 0);
                const hits = day.rounds.filter(r => r.selectedBucket === r.actualBucket).length;
                const tooHigh = day.rounds.filter(r => r.selectedBucket > r.actualBucket).length;
                const tooLow = day.rounds.filter(r => r.selectedBucket < r.actualBucket).length;
                statsLines = [
                    { label: "Points", value: String(total) },
                    { label: "Rounds", value: String(day.rounds.length) },
                    { label: "H/H/L", value: `${hits}/${tooHigh}/${tooLow}` },
                ];
                kdaText = `${hits} / ${tooHigh + tooLow} / 0`;
            }
        }
    } else {
        // Fallback generic card
        statsLines = [
            { label: "Play", value: "steam5.app/review-guesser" },
            { label: "Daily challenge", value: "Guess review buckets" },
        ];
    }

    const accent = primary;
    const accentDim = "rgba(255,255,255,0.08)";

    const panel = (
        <div
            style={{
                width: "100%",
                height: "100%",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                background: bg,
                padding: 40,
            }}
        >
            <div
                style={{
                    width: 1000,
                    height: 500,
                    display: "flex",
                    flexDirection: "column",
                    background: panelBg,
                    border: `2px solid ${panelBorder}`,
                    borderRadius: 24,
                    padding: 32,
                    color: "#e5e7eb",
                    position: "relative",
                }}
            >
                <div style={{ position: "absolute", inset: 0, borderRadius: 24, boxShadow: `inset 0 0 0 4px ${accentDim}` }} />
                <div style={{ display: "flex", gap: 24, alignItems: "center" }}>
                    <div style={{ width: 128, height: 128, borderRadius: 16, overflow: "hidden", background: "#111827", border: `2px solid ${accent}` }}>
                        {avatarSrc ? (
                            // eslint-disable-next-line @next/next/no-img-element
                            <img src={avatarSrc} alt="avatar" width={128} height={128} style={{ objectFit: "cover", width: "100%", height: "100%" }} />
                        ) : (
                            // eslint-disable-next-line @next/next/no-img-element
                            <img src={iconDataUri} alt="Steam5" width={128} height={128} />
                        )}
                    </div>
                    <div style={{ display: "flex", flexDirection: "column", gap: 8, flex: 1 }}>
                        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                            <div style={{ fontSize: 48, fontWeight: 700 }}>{title}</div>
                            {badge && (
                                <div style={{
                                    padding: "6px 10px",
                                    background: badge.color,
                                    color: "#0b1220",
                                    borderRadius: 999,
                                    fontSize: 24,
                                    fontWeight: 700,
                                }}>{badge.label}</div>
                            )}
                        </div>
                        <div style={{ color: "#9ca3af", fontSize: 24 }}>{subtitle}</div>
                    </div>
                </div>

                <div style={{ display: "flex", gap: 24, marginTop: 24 }}>
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: 12, flex: 1 }}>
                        {statsLines.map((s, i) => (
                            <div key={i} style={{ background: "#0b1220", border: `1px solid ${panelBorder}`, borderRadius: 12, padding: 12 }}>
                                <div style={{ color: "#9ca3af", fontSize: 18 }}>{s.label}</div>
                                <div style={{ fontSize: 28, fontWeight: 700 }}>{s.value}</div>
                            </div>
                        ))}
                    </div>
                    <div style={{ width: 280, display: "flex", flexDirection: "column", gap: 12 }}>
                        <div style={{ background: "#0b1220", border: `1px solid ${panelBorder}`, borderRadius: 12, padding: 12 }}>
                            <div style={{ color: "#9ca3af", fontSize: 18 }}>Recent KDA</div>
                            <div style={{ fontSize: 36, fontWeight: 700, color: accent }}>{kdaText || "—"}</div>
                        </div>
                        <div style={{ background: "#0b1220", border: `1px solid ${panelBorder}`, borderRadius: 12, padding: 12 }}>
                            <div style={{ color: "#9ca3af", fontSize: 18 }}>Team</div>
                            <div style={{ display: "flex", gap: 6, marginTop: 8 }}>
                                <div style={{ width: 20, height: 20, background: accent, borderRadius: 4 }} />
                                <div style={{ width: 20, height: 20, background: panelBorder, borderRadius: 4 }} />
                                <div style={{ width: 20, height: 20, background: "#e5e7eb", borderRadius: 4 }} />
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );

    return new ImageResponse(panel, {
        width: size.width,
        height: size.height,
        fonts: [
            ...(fontRegular ? [{ name: "Inter", data: fontRegular, weight: 400 as const, style: "normal" as const }] : []),
            ...(fontBold ? [{ name: "Inter", data: fontBold, weight: 700 as const, style: "normal" as const }] : []),
        ],
    });
}


