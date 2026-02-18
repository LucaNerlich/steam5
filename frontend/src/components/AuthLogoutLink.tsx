"use client";

import React from 'react';
import Link from 'next/link';
import useAuthSignedIn from "@/lib/hooks/useAuthSignedIn";

export default function AuthLogoutLink(): React.ReactElement | null {
    const signedIn = useAuthSignedIn();

    if (!signedIn) return null;
    return <Link href="/api/auth/logout" className="btn">Logout</Link>;
}


