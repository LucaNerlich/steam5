"use client";

import React, {useEffect, useRef} from "react";
import {clearAll} from "@/lib/storage";
import Image from "next/image";
import SignInImage from "../../public/sign-in-through-steam.png"
import {UserCheckIcon} from "@phosphor-icons/react/ssr";
import Link from "next/link";
import {useAuth} from "@/contexts/AuthContext";

export function buildSteamLoginUrl(): string {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    const envBase = process.env.NEXT_PUBLIC_DOMAIN;
    const callbackBase = (typeof window !== 'undefined' && (!envBase || envBase.length === 0)) ? window.location.origin : (envBase || '');
    const callback = `${callbackBase}/api/auth/steam/callback`;
    return `${backend}/api/auth/steam/login?redirect=${encodeURIComponent(callback)}`;
}

export default function SteamLoginButton(): React.ReactElement {
    const {isSignedIn, steamId} = useAuth();
    const clearedRef = useRef(false);

    useEffect(() => {
        if (isSignedIn && !clearedRef.current) {
            clearedRef.current = true;
            try { clearAll(); } catch { /* ignore */ }
        }
    }, [isSignedIn]);

    const onClick = () => {
        // Fix #6: generate a random CSRF state token and store it in a short-lived
        // client-side cookie.  The token is appended to the backend login URL; the
        // backend embeds it in openid.return_to so Steam carries it back in the
        // redirect.  The Next.js callback route then verifies the URL param matches
        // the cookie before proceeding, preventing login-CSRF attacks.
        const state = crypto.randomUUID().replace(/-/g, '');
        const secure = location.protocol === 'https:' ? '; Secure' : '';
        document.cookie = `s5_state=${state}; path=/; max-age=300; samesite=lax${secure}`;
        window.location.href = `${buildSteamLoginUrl()}&state=${encodeURIComponent(state)}`;
    };

    if (steamId) {
        return (
            <div style={{display: 'flex', alignItems: 'center', gap: '0.5rem'}}>
                <Link href={`/profile/${steamId}`}
                      style={{display: 'flex', alignItems: 'center', gap: '0.15rem'}}
                      title="Your Profile">
                    <span className="mobile__hide">Profile</span><UserCheckIcon size={28}/>
                </Link>
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


