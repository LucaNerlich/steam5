// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest';
import {SCREENSHOT_FANCYBOX_OPTIONS, screenshotCaption} from './ReviewGuesserHero';

describe('screenshotCaption', () => {
    it('renders publisher markup as text, not HTML', () => {
        const el = screenshotCaption(null, {caption: '<img src=x onerror="alert(1)">'});
        expect(el.textContent).toBe('<img src=x onerror="alert(1)">');
        expect(el.children).toHaveLength(0);
    });

    it('wires the formatter into the options passed to Fancybox.bind', () => {
        const el = SCREENSHOT_FANCYBOX_OPTIONS.Carousel.formatCaption(null, {
            caption: '<img src=x onerror="alert(1)">',
        }) as HTMLElement;
        expect(el.textContent).toBe('<img src=x onerror="alert(1)">');
        expect(el.children).toHaveLength(0);
    });
});
