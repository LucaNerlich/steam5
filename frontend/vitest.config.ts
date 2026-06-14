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
        include: ['src/**/*.test.ts', 'app/**/*.test.ts'],
        // Pin timezone and locale so date/time/number formatting is deterministic
        // regardless of where the suite runs (CI, contributors' machines).
        env: {TZ: 'UTC', LANG: 'en-US'},
    },
});
