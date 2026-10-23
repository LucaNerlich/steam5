import {readFileSync} from 'node:fs';
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
        const gradle = readFileSync(new URL('../../../backend/build.gradle.kts', import.meta.url), 'utf8');

        const nextMajor = pkg.dependencies.next.match(/\d+/)?.[0];
        const springMajor = gradle.match(/org\.springframework\.boot"\) version "(\d+)/)?.[1];
        expect(nextMajor).toBeTruthy();
        expect(springMajor).toBeTruthy();

        const body = await GET().text();
        expect(body).toContain(`Next.js ${nextMajor}`);
        expect(body).toContain(`Spring Boot ${springMajor}`);
    });
});
