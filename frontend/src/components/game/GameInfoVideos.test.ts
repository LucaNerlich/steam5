import {describe, expect, it, vi} from 'vitest';
import {resolveMovieSource, textCaption} from './GameInfoVideos';
import type {Movie} from '@/types/review-game';

// The suite runs in the node environment, so the single DOM API the caption formatter
// touches is stubbed: an element with the two properties that decide whether a caption
// becomes text or markup. innerHTML is recorded so a regression to the default Fancybox
// renderer (string caption through innerHTML) fails loudly.
class FakeElement {
    textContent: string | null = null;
    innerHTML: string | null = null;
}

vi.stubGlobal('document', {createElement: () => new FakeElement()});

describe('textCaption', () => {
    it('carries markup-bearing movie names as text, never as markup', () => {
        const caption = '<img src=x onerror=alert(document.cookie)>';
        const el = textCaption(caption) as unknown as FakeElement;

        expect(el.textContent).toBe(caption);
        expect(el.innerHTML).toBeNull();
    });

    it('renders a missing caption as empty text', () => {
        const el = textCaption(undefined) as unknown as FakeElement;

        expect(el.textContent).toBe('');
        expect(el.innerHTML).toBeNull();
    });
});

function movie(overrides: Partial<Movie>): Movie {
    return {
        id: 1,
        name: 'Trailer',
        thumbnail: 'https://cdn.example/thumb.jpg',
        webm: null,
        mp4: null,
        dashAv1: null,
        dashH264: null,
        hlsH264: null,
        ...overrides,
    };
}

describe('resolveMovieSource', () => {
    it('prefers H.264 DASH over an AV1 stream a browser may not decode', () => {
        const picked = resolveMovieSource(movie({
            dashAv1: 'https://cdn.example/dash_av1.mpd',
            dashH264: 'https://cdn.example/dash_264.mpd',
        }));

        expect(picked.src).toBe('https://cdn.example/dash_264.mpd');
        expect(picked.format).toBe('application/dash+xml');
    });

    it('prefers a progressive MP4 over streams that need dash.js or native HLS', () => {
        const picked = resolveMovieSource(movie({
            mp4: 'https://cdn.example/trailer.mp4',
            dashH264: 'https://cdn.example/dash_264.mpd',
            hlsH264: 'https://cdn.example/hls_264_master.m3u8',
        }));

        expect(picked.src).toBe('https://cdn.example/trailer.mp4');
        expect(picked.type).toBeUndefined();
    });

    it('prefers H.264 HLS over AV1 DASH when only those exist (Safari plays HLS natively)', () => {
        const picked = resolveMovieSource(movie({
            hlsH264: 'https://cdn.example/hls_264_master.m3u8',
            dashAv1: 'https://cdn.example/dash_av1.mpd',
        }));

        expect(picked.src).toBe('https://cdn.example/hls_264_master.m3u8');
        expect(picked.format).toBe('application/vnd.apple.mpegurl');
    });

    it('still picks a DASH manifest when no progressive file exists', () => {
        const picked = resolveMovieSource(movie({
            dashAv1: 'https://cdn.example/dash_av1.mpd',
        }));

        expect(picked.src).toBe('https://cdn.example/dash_av1.mpd');
        expect(picked.format).toBe('application/dash+xml');
    });

    it('normalizes an http stream to https', () => {
        const picked = resolveMovieSource(movie({mp4: 'http://cdn.example/trailer.mp4'}));

        expect(picked.src).toBe('https://cdn.example/trailer.mp4');
    });
});
