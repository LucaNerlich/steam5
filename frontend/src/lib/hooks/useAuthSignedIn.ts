"use client";

import {useEffect, useState} from "react";

export default function useAuthSignedIn(): boolean | null {
    const [signedIn, setSignedIn] = useState<boolean | null>(null);

    useEffect(() => {
        let cancelled = false;

        async function check() {
            try {
                const res = await fetch('/api/auth/me', {cache: 'no-store'});
                const json = await res.json();
                if (!cancelled) setSignedIn(Boolean(json?.signedIn));
            } catch {
                if (!cancelled) setSignedIn(false);
            }
        }

        check();
        return () => {
            cancelled = true;
        };
    }, []);

    return signedIn;
}


