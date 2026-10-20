"use client";

import React, {useEffect, useRef} from "react";
import {clearDay} from "@/lib/storage";
import Image from "next/image";
import SignInImage from "../../public/sign-in-through-steam.png"
import {UserCheckIcon} from "@phosphor-icons/react/ssr";
import Link from "next/link";
import {useAuth} from "@/contexts/AuthContext";
import Avatar from "@/components/Avatar";
import {startSteamLogin} from "@/lib/steamLogin";

/** Clears only today's guest copy; archive days are local-only and must survive. */
export function clearTodayGuestProgress(now: Date = new Date()): void {
    try {
        clearDay(now.toISOString().slice(0, 10));
    } catch {
        // Storage unavailable: nothing to clear.
    }
}

/**
 * Renders a Steam sign-in control or the signed-in user's profile link.
 *
 * @returns The Steam sign-in button or the user's profile link.
 */
export default function SteamLoginButton(): React.ReactElement {
    const {isSignedIn, steamId, avatar} = useAuth();
    const clearedRef = useRef(false);

    useEffect(() => {
        if (isSignedIn && !clearedRef.current) {
            clearedRef.current = true;
            clearTodayGuestProgress();
        }
    }, [isSignedIn]);

    const onClick = () => {
        startSteamLogin();
    };

    if (steamId) {
        return (
            <div style={{display: 'flex', alignItems: 'center', gap: '0.5rem'}}>
                <Link href={`/profile/${steamId}`}
                      style={{display: 'flex', alignItems: 'center', gap: '0.15rem'}}
                      title="Your Profile">
                    <span className="mobile__hide">Profile</span>
                    {avatar ? (
                        <Avatar src={avatar} size={28}/>
                    ) : (
                        <UserCheckIcon size={28}/>
                    )}
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


