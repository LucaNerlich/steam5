// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest';
import {screenshotCaption} from './ReviewGuesserHero';

describe('screenshotCaption', () => {
    it('renders publisher markup as text, not HTML', () => {
        const el = screenshotCaption(null, {caption: '<img src=x onerror="alert(1)">'});
        expect(el.textContent).toBe('<img src=x onerror="alert(1)">');
        expect(el.children).toHaveLength(0);
    });
});
