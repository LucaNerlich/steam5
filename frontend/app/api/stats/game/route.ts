import {NextResponse} from "next/server";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
const revalidate = 600; // 10 minutes

export async function GET() {
    try {
        const url = `${BACKEND_ORIGIN}/api/stats/game?topGamesLimit=10`;
        const res = await fetch(url, {
            headers: {accept: 'application/json'},
            next: {revalidate, tags: ['stats:game']},
        });

        if (!res.ok) {
            const errorText = await res.text();
            console.error(`[Game Stats API] Response status: ${res.status}, body: ${errorText}`);
            return NextResponse.json({error: "Failed to load game statistics"}, {status: res.status});
        }

        const data = await res.json();
        return NextResponse.json(data, {
            headers: {
                'Cache-Control': 'public, s-maxage=600, max-age=600',
            },
        });
    } catch (error) {
        console.error('[Game Stats API] Error:', error);
        return NextResponse.json({error: "Failed to load game statistics"}, {status: 502});
    }
}

