import {describe, expect, it} from 'vitest';
import {GET} from './route';

describe('/llms.txt', () => {
    it('describes the real scoring rule and stack versions', async () => {
        const body = await GET().text();

        expect(body).not.toContain('accuracy and speed');
        expect(body).toContain('exact match scores 5 points');
        expect(body).not.toContain('Next.js 15');
        expect(body).toContain('Next.js 16');
        expect(body).toContain('Spring Boot 4');
    });
});
