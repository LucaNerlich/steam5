import {describe, expect, it} from 'vitest';
import {storedSelection} from './ArchiveOfflineRound';

describe('storedSelection', () => {
    it('restores a submitted round', () => {
        expect(storedSelection({results: {2: {selectedLabel: '1-13'}}}, 2))
            .toEqual({selectedLabel: '1-13', submitted: true});
    });

    it('clears the selection after the shared day is reset', () => {
        expect(storedSelection(null, 2)).toEqual({selectedLabel: null, submitted: false});
        expect(storedSelection({results: {}}, 2)).toEqual({selectedLabel: null, submitted: false});
    });

    it('ignores a result stored for a different app at the same round', () => {
        // Picks regenerated for the same date leave the old guess at the same round index.
        const stored = {results: {2: {selectedLabel: '1-13', appId: 111}}};
        expect(storedSelection(stored, 2, 222)).toEqual({selectedLabel: null, submitted: false});
        expect(storedSelection(stored, 2, 111)).toEqual({selectedLabel: '1-13', submitted: true});
    });
});
