import {cookies} from 'next/headers';

/**
 * Reads the server-side Steam session token. Centralized auth gate for
 * server actions: exported server actions are public POST endpoints, so
 * every privileged action must call this before touching data.
 */
export async function getAuth(): Promise<string | undefined> {
    return (await cookies()).get('s5_token')?.value;
}
