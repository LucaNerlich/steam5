"use client";

import React, {useEffect, useState} from 'react';

export default function AuthLogoutLink(): React.ReactElement | null {
    const [signedIn, setSignedIn] = useState<boolean>(false);

    useEffect(() => {
        let active = true;

        async function load() {
            try {
                const res = await fetch('/api/auth/me', {cache: 'no-store'});
                if (!res.ok) return;
                const data = await res.json();
                if (active) setSignedIn(Boolean(data?.signedIn));
            } catch {
            }
        }

        load();
        return () => {
            active = false
        };
    }, []);

    if (!signedIn) return null;
    return <a href="/api/auth/logout" className="btn">Logout</a>;
}


