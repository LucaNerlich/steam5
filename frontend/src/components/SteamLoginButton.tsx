"use client";

import React, {useEffect, useState} from "react";

export default function SteamLoginButton(): React.ReactElement {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    const [steamId, setSteamId] = useState<string | null>(null);

    useEffect(() => {
        let active = true;

        async function load() {
            try {
                const res = await fetch('/api/auth/me', {cache: 'no-store'});
                if (!res.ok) return;
                const data = await res.json();
                if (active) setSteamId(data?.signedIn ? data.steamId : null);
            } catch {
            }
        }

        load();
        return () => {
            active = false
        };
    }, []);

    const onClick = () => {
        const callback = `${window.location.origin}/api/auth/steam/callback`;
        window.location.href = `${backend}/api/auth/steam/login?redirect=${encodeURIComponent(callback)}`;
    };

    if (steamId) {
        return (
            <div style={{display: 'flex', alignItems: 'center', gap: '0.5rem'}}>
                <span className="text-muted">Signed in</span>
                <a href="/api/auth/logout" className="btn-ghost">Logout</a>
            </div>
        );
    }

    return (
        <button className="btn-ghost" onClick={onClick} aria-label="Sign in with Steam">
            Sign in with Steam
        </button>
    );
}


