"use client";

import useSWR from "swr";

type AuthMeData = { signedIn?: boolean; steamId?: string | null };

const authMeFetcher = async (url: string): Promise<AuthMeData> => {
    const r = await fetch(url, {cache: 'no-store'});
    if (r.status === 401) return {signedIn: false};
    if (!r.ok) throw new Error(`Auth check failed (${r.status})`);
    return r.json();
};

export function useAuthMe(): { data: AuthMeData | undefined; isLoading: boolean } {
    const {data, isLoading} = useSWR<AuthMeData>('/api/auth/me', authMeFetcher, {
        revalidateOnFocus: false,
        dedupingInterval: 30000,
        errorRetryCount: 2,
    });
    return {data, isLoading};
}

export default function useAuthSignedIn(): boolean | null {
    const {data} = useAuthMe();
    if (data === undefined) return null;
    return Boolean(data?.signedIn);
}


