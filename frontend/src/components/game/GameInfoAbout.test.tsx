import {describe, expect, it} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';
import React from 'react';
import GameInfoAbout from './GameInfoAbout';

describe('GameInfoAbout', () => {
    it('drops script and style nodes instead of rendering them', () => {
        // htmlparser2 types these as "script"/"style", not "tag"; the allowlist branch never
        // saw them and the default renderer emitted the tags with all their attributes.
        const html = renderToStaticMarkup(
            <GameInfoAbout html={'<p>Hello</p><script src="https://evil.example/x.js">alert(1)</script><style>body{display:none}</style>'}/>,
        );

        expect(html).toContain('<p>Hello</p>');
        expect(html).not.toContain('<script');
        expect(html).not.toContain('<style');
    });

    it('drops an img whose src is not http(s)', () => {
        // The img branch used to skip isSafeUrl; a data: URL still renders when the allowlist
        // is removed, which the ftp:// case did not prove.
        const html = renderToStaticMarkup(
            <GameInfoAbout html={'<p>Hi</p><img src="data:image/svg+xml,<svg onload=alert(1)>" alt="x"/>'}/>,
        );

        expect(html).not.toContain('<img');
        expect(html).toContain('<p>Hi</p>');
    });

    it('drops an img whose scheme is not lower-case', () => {
        // isSafeUrl lowercases the scheme; normalizeToHttps does not, and returned "".
        const html = renderToStaticMarkup(
            <GameInfoAbout html={'<p>Hi</p><img src="HTTPS://cdn.example/x.png" alt="x"/>'}/>,
        );

        expect(html).not.toContain('<img');
    });
});
