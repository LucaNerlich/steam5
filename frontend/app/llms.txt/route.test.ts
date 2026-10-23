import {existsSync, readFileSync} from 'node:fs';
import {describe, expect, it} from 'vitest';
import {GET} from './route';

describe('/llms.txt', () => {
    it('describes the real scoring rule', async () => {
        const body = await GET().text();

        expect(body).not.toContain('accuracy and speed');
        expect(body).toContain('exact match scores 5 points');
    });

    it('advertises the versions the manifests actually declare', async () => {
        const pkg = JSON.parse(readFileSync(new URL('../../package.json', import.meta.url), 'utf8')) as {
            dependencies: {next: string};
        };

        const nextMajor = pkg.dependencies.next.match(/\d+/)?.[0];
        expect(nextMajor).toBeTruthy();

        const body = await GET().text();
        expect(body).toContain(`Next.js ${nextMajor}`);

        // Coolify builds the frontend from its own directory, so the backend manifest is
        // outside that build context. Check Spring drift only where the repo root exists.
        const gradleUrl = new URL('../../../backend/build.gradle.kts', import.meta.url);
        if (existsSync(gradleUrl)) {
            const gradle = readFileSync(gradleUrl, 'utf8');
            const springMajor = gradle.match(/org\.springframework\.boot"\) version "(\d+)/)?.[1];
            expect(springMajor).toBeTruthy();
            expect(body).toContain(`Spring Boot ${springMajor}`);
        }
    });
});
