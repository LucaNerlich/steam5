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
        // The img branch used to skip isSafeUrl, so a publisher-supplied ftp:// URL reached
        // next/image as an empty src instead of being dropped like any other URL attribute.
        const html = renderToStaticMarkup(
            <GameInfoAbout html={'<p>Hi</p><img src="ftp://cdn.example/x.png" alt="x"/>'}/>,
        );

        expect(html).not.toContain('<img');
        expect(html).toContain('<p>Hi</p>');
    });
});
