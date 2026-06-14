"use client";

import React, { createContext, useContext, useState, useCallback, useEffect } from "react";
import type { ReactNode } from "react";
import { usePathname } from "next/navigation";
import useSWR from "swr";

type AuthState = {
    isSignedIn: boolean;
    steamId?: string | null;
    isLoading?: boolean;
};

type AuthContextType = AuthState & {
    refreshAuth: () => void;
};

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const authFetcher = async (url: string): Promise<AuthState> => {
    const r = await fetch(url, { cache: 'no-store' });
    if (r.status === 401) return { isSignedIn: false };
    if (!r.ok) throw new Error(`Auth check failed (${r.status})`);
    const data = await r.json();
    return {
        isSignedIn: Boolean(data?.signedIn),
        steamId: data?.steamId || null,
    };
};

export function AuthProvider({
    children,
    initialAuth,
}: {
    children: ReactNode;
    initialAuth: AuthState;
}) {
    const [auth, setAuth] = useState<AuthState>(initialAuth);

    // Use SWR for client-side auth refresh with the initial server data
    const { data, mutate } = useSWR<AuthState>('/api/auth/me', authFetcher, {
        fallbackData: initialAuth,
        // Re-check auth when the user returns to the tab so a stale signed-in state
        // (e.g. cookie expired/dropped while the SPA stayed open) self-corrects
        // without requiring a full page reload.
        revalidateOnFocus: true,
        dedupingInterval: 30000,
        errorRetryCount: 2,
    });

    // Update auth state when SWR data changes
    useEffect(() => {
        if (data) {
            setAuth(data);
        }
    }, [data]);

    // Re-check auth on client-side navigation. The provider lives in the root
    // layout and does not remount between routes, so without this a session that
    // was lost mid-visit (e.g. cookie expired/dropped) would keep showing as
    // signed-in in the header until a guess is submitted or the tab is refocused.
    const pathname = usePathname();
    useEffect(() => {
        void mutate();
    }, [pathname, mutate]);

    const refreshAuth = useCallback(() => {
        void mutate();
    }, [mutate]);

    return (
        <AuthContext.Provider
            value={{
                ...auth,
                refreshAuth,
            }}
        >
            {children}
        </AuthContext.Provider>
    );
}

export function useAuth(): AuthContextType {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error("useAuth must be used within an AuthProvider");
    }
    return context;
}

// Backward compatibility hook that matches the old useAuthSignedIn interface
export function useAuthSignedIn(): boolean | null {
    const { isSignedIn, isLoading } = useAuth();
    if (isLoading) return null;
    return isSignedIn;
}