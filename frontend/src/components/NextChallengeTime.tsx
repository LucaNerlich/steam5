"use client";

import useSWR from "swr";
import { useMemo } from "react";

const fetcher = (url: string) => fetch(url, {headers: {accept: 'application/json'}}).then(r => {
    if (!r.ok) throw new Error(`Failed to load ${url}: ${r.status}`);
    return r.json();
});

export default function NextChallengeTime() {
    const { data: nextChallengeData } = useSWR<{nextChallengeTime: string, serverTimezoneOffset: number}>(
        '/api/review-game/next-challenge-time',
        fetcher,
        {
            refreshInterval: 60000, // Refresh every minute
            revalidateOnFocus: true,
        }
    );

    const nextChallengeTimeLocal = useMemo(() => {
        if (!nextChallengeData?.nextChallengeTime) return null;
        
        try {
            // Parse ISO-8601 datetime string (e.g., "2025-11-12T00:01Z")
            // The 'Z' indicates UTC time, JavaScript Date will automatically convert to local timezone
            const utcDate = new Date(nextChallengeData.nextChallengeTime);
            
            // getHours() and getMinutes() automatically return local time values
            const hours = utcDate.getHours();
            const mins = utcDate.getMinutes();
            const period = hours >= 12 ? 'PM' : 'AM';
            const displayHours = hours === 0 ? 12 : hours > 12 ? hours - 12 : hours;
            
            return `${displayHours}:${mins.toString().padStart(2, '0')} ${period}`;
        } catch (e) {
            console.error('Error parsing next challenge time:', e);
            return null;
        }
    }, [nextChallengeData]);

    if (!nextChallengeTimeLocal) return null;

    return (
        <>Next daily challenge at: <span style={{opacity: 0.7}}>{nextChallengeTimeLocal}</span></>
    );
}

