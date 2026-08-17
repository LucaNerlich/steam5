"use client";

import React from 'react';
import {useAuthSignedIn} from "@/contexts/AuthContext";

export default function AuthLogoutLink(): React.ReactElement | null {
    const signedIn = useAuthSignedIn();

    if (!signedIn) return null;
    // POST (not a link): SameSite=Lax cookies are not sent on cross-site POSTs,
    // so a third-party page cannot force-logout the user with an <img>/GET.
    return (
        <form action="/api/auth/logout" method="POST">
            <button type="submit" className="btn">Logout</button>
        </form>
    );
}
