"use client";

import useSWR from "swr";

type AuthMeData = { signedIn?: boolean; steamId?: string | null };

const authMeFetcher = (url: string) =>
    fetch(url, {cache: 'no-store'}).then(r => {
        if (!r.ok) return {signedIn: false} as AuthMeData;
        return r.json() as Promise<AuthMeData>;
    });

export function useAuthMe(): { data: AuthMeData | undefined; isLoading: boolean } {
    const {data, isLoading} = useSWR<AuthMeData>('/api/auth/me', authMeFetcher, {
        revalidateOnFocus: false,
        dedupingInterval: 60000,
    });
    return {data, isLoading};
}

export default function useAuthSignedIn(): boolean | null {
    const {data} = useAuthMe();
    if (data === undefined) return null;
    return Boolean(data?.signedIn);
}


