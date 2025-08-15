"use client";

import React from "react";

export default function SteamLoginButton(): React.ReactElement {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    const onClick = () => {
        const callback = `${window.location.origin}/api/auth/steam/callback`;
        window.location.href = `${backend}/api/auth/steam/login?redirect=${encodeURIComponent(callback)}`;
    };
    return (
        <button className="btn-ghost" onClick={onClick} aria-label="Sign in with Steam">
            Sign in with Steam
        </button>
    );
}


