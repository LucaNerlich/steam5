"use client";

import React, {useEffect, useState} from "react";
import {clearAll} from "@/lib/storage";
import Image from "next/image";
import SignInImage from "../../public/sign-in-through-steam.png"

export function buildSteamLoginUrl(): string {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    const envBase = process.env.NEXT_PUBLIC_DOMAIN;
    const callbackBase = (typeof window !== 'undefined' && (!envBase || envBase.length === 0)) ? window.location.origin : (envBase || '');
    const callback = `${callbackBase}/api/auth/steam/callback`;
    return `${backend}/api/auth/steam/login?redirect=${encodeURIComponent(callback)}`;
}

export default function SteamLoginButton(): React.ReactElement {
    const [steamId, setSteamId] = useState<string | null>(null);

    useEffect(() => {
        let active = true;

        async function load() {
            try {
                const res = await fetch('/api/auth/me', {cache: 'no-store'});
                if (!res.ok) return;
                const data = await res.json();
                if (active) setSteamId(data?.signedIn ? data.steamId : null);
                // If just logged in, clear any local storage progress to avoid conflicts
                try {
                    if (data?.signedIn) clearAll();
                } catch {
                }
            } catch {
            }
        }

        load();
        return () => {
            active = false
        };
    }, []);

    const onClick = () => {
        const url = buildSteamLoginUrl();
        try {
            console.debug('steam5 login url', url);
        } catch {
        }
        window.location.href = url;
    };

    if (steamId) {
        return (
            <div style={{display: 'flex', alignItems: 'center', gap: '0.5rem'}}>
                <span className="text-muted">Signed in</span>
            </div>
        );
    }

    return (
        <button className="steam-login-btn" onClick={onClick} aria-label="Sign in through Steam">
            <Image
                src={SignInImage}
                alt="Sign in through Steam"
                width={184}
                height={44}
                placeholder='blur'
                priority
            />
        </button>
    );
}


