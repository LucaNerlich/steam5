import type {ReactNode} from 'react';
import {describe, expect, it, vi} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';

// Avoid pulling in Next's App Router context / request-scoped APIs for a plain
// string-matching render test — mirrors the mocking approach used by actions.test.ts.
vi.mock('next/headers', () => ({
    cookies: async () => ({get: () => undefined}),
}));
vi.mock('next/link', () => ({
    default: ({href, className, children}: {href: string; className?: string; children?: ReactNode}) => (
        <a href={href} className={className}>{children}</a>
    ),
}));

import {CommentBodyText, insertMentionIntoBody, insertMentionReference} from './DayComments';

function render(body: string): string {
    return renderToStaticMarkup(<CommentBodyText body={body}/>);
}

describe('CommentBodyText mentions', () => {
    it('renders a mention token as a link to the profile page', () => {
        const html = render('Hey [@Alice](mention:76500000000000001), nice guess!');

        expect(html).toContain('href="/profile/76500000000000001"');
        expect(html).toContain('@Alice');
    });

    it('preserves surrounding plain text around a mention', () => {
        const html = render('Hey [@Alice](mention:76500000000000001), nice guess!');

        expect(html).toContain('Hey ');
        expect(html).toContain(', nice guess!');
    });

    it('strips brackets from the mention label for defense in depth', () => {
        // The label capture group allows '[' (only ']' terminates it), so a stray
        // '[' can still reach sanitizeMentionLabel, which strips it before rendering.
        const html = render('cc [@Al[ice](mention:76500000000000001)');

        expect(html).not.toContain('Al[ice');
        expect(html).toContain('@Alice');
    });

    it('renders plain text with no links when there are no tokens', () => {
        const html = render('just a normal comment, no refs here');

        expect(html).not.toContain('<a');
        expect(html).toContain('just a normal comment, no refs here');
    });

    it('leaves a mention with a non-numeric steamId unlinked instead of rendering it as an href', () => {
        const html = render('sketchy [@Alice](mention:../) mention');

        expect(html).not.toContain('href="/profile/../"');
        expect(html).not.toContain('<a');
        expect(html).toContain('[@Alice](mention:../)');
    });

    it('renders both a game link and a mention together in caret order', () => {
        const html = render(
            'ping [@Alice](mention:76500000000000001) about [Half-Life 2](https://store.steampowered.com/app/220)',
        );

        expect(html).toContain('href="/profile/76500000000000001"');
        expect(html).toContain('href="https://store.steampowered.com/app/220"');
        const mentionIndex = html.indexOf('/profile/76500000000000001');
        const gameIndex = html.indexOf('/app/220');
        expect(mentionIndex).toBeGreaterThan(-1);
        expect(gameIndex).toBeGreaterThan(mentionIndex);
    });
});

describe('insertMentionIntoBody', () => {
    const snippet = '[@Alice](mention:7)';

    it('replaces the token that ends at the caret and moves the caret after it', () => {
        const result = insertMentionIntoBody('hello @al', 9, snippet, 1000);

        expect(result).toEqual({value: `hello ${snippet}`, caret: `hello ${snippet}`.length});
    });

    it('closes the menu without inserting when the caret moved left of the token', () => {
        // Home/End and arrow keys move the caret without firing an input event; using the
        // menu's stored token start here duplicated the text between caret and token.
        expect(insertMentionIntoBody('hello @al', 0, snippet, 1000)).toBeNull();
    });

    it('closes the menu without inserting when the caret moved right past the token', () => {
        expect(insertMentionIntoBody('hi @al there', 12, snippet, 1000)).toBeNull();
    });

    it('leaves the text that follows the token intact', () => {
        const result = insertMentionIntoBody('hi @al and more', 6, snippet, 1000);

        expect(result).toEqual({
            value: `hi ${snippet} and more`,
            caret: `hi ${snippet}`.length,
        });
    });

    it('inserts a separating space when the following character is not whitespace', () => {
        const result = insertMentionIntoBody('hi @al, see', 6, snippet, 1000);

        expect(result).toEqual({
            value: `hi ${snippet} , see`,
            caret: `hi ${snippet} `.length,
        });
    });

    it('refuses an insertion that would exceed the body limit', () => {
        expect(insertMentionIntoBody('hi @al', 6, snippet, 3)).toBeNull();
    });
});

describe('insertMentionReference', () => {
    // Drives the real call path (menu pick reads the caret from the live textarea)
    // with a plain stub object — the vitest env has no DOM. For value 'hello @al'
    // the token's '@' sits at index 6, so a caret read from the stale stored token
    // start (6) differs from both live carets used below and would fail these tests.
    const candidate = {steamId: '7', personaName: 'Alice'};
    const snippet = '[@Alice](mention:7)';

    function stubTextarea(value: string, selectionStart: number) {
        const focus = vi.fn();
        const setSelectionRange = vi.fn();
        const ta = {value, selectionStart, focus, setSelectionRange} as unknown as HTMLTextAreaElement;
        return {ta, focus, setSelectionRange};
    }

    it('closes the menu without inserting when the live caret is left of the token', () => {
        //selectionStart 0 is left of the '@' at index 6: a caret taken from the stale
        // stored token start (6) would insert here and mutate the value instead.
        const {ta, focus, setSelectionRange} = stubTextarea('hello @al', 0);

        expect(insertMentionReference(ta, candidate, 1000)).toBeNull();
        expect(ta.value).toBe('hello @al');
        expect(focus).not.toHaveBeenCalled();
        expect(setSelectionRange).not.toHaveBeenCalled();
    });

    it('inserts the reference at the live caret and returns the new length', () => {
        // selectionStart 9 is the token end: insertion replaces exactly the token.
        // A caret taken from the stale token start (6) would leave '@al' behind and
        // return the longer length of `hello ${snippet} @al`.
        const {ta, focus, setSelectionRange} = stubTextarea('hello @al', 9);

        expect(insertMentionReference(ta, candidate, 1000)).toBe(`hello ${snippet}`.length);
        expect(ta.value).toBe(`hello ${snippet}`);
        expect(focus).toHaveBeenCalled();
        expect(setSelectionRange).toHaveBeenCalledWith(`hello ${snippet}`.length, `hello ${snippet}`.length);
    });
});
