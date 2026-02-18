"use client";

import React from 'react';
import useAuthSignedIn from "@/lib/hooks/useAuthSignedIn";

export default function AuthLogoutLink(): React.ReactElement | null {
    const signedIn = useAuthSignedIn();

    if (!signedIn) return null;
    // Plain <a> — next/link would prefetch this API route and trigger logout
    return <a href="/api/auth/logout" className="btn">Logout</a>;
}


