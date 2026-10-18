import {defineConfig} from 'vitest/config';
import {fileURLToPath} from 'node:url';

export default defineConfig({
    resolve: {
        alias: {
            '@': fileURLToPath(new URL('./src', import.meta.url)),
        },
    },
    test: {
        environment: 'node',
        include: ['src/**/*.test.ts', 'src/**/*.test.tsx', 'app/**/*.test.ts'],
        // Pin timezone, locale, and NODE_ENV so behavior is deterministic regardless of
        // where the suite runs. NODE_ENV matters: build environments (Railpack/Coolify)
        // export NODE_ENV=production, which would resolve React's production entry —
        // where `act` does not exist — and fail the suite during `pnpm build`.
        env: {TZ: 'UTC', LANG: 'en-US', NODE_ENV: 'test'},
    },
});
