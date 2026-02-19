"use client";

import React, { createContext, useContext, useState, useCallback, useEffect } from "react";
import type { ReactNode } from "react";
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
        revalidateOnFocus: false,
        dedupingInterval: 30000,
        errorRetryCount: 2,
    });

    // Update auth state when SWR data changes
    useEffect(() => {
        if (data) {
            setAuth(data);
        }
    }, [data]);

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