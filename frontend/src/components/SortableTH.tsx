"use client";

export type SortDir = 'asc' | 'desc';

export type SortableTHProps<K extends string> = {
    label: string;
    keyName: K;
    activeKey: K | null;
    direction: SortDir;
    onSort: (key: K) => void;
    title?: string;
    alignNum?: boolean;
};

/**
 * A reusable sortable table header cell. Renders an accessible button that
 * toggles sort state on the parent via the `onSort` callback and reflects the
 * active state through `aria-sort` and a small arrow indicator.
 */
export default function SortableTH<K extends string>({
    label,
    keyName,
    activeKey,
    direction,
    onSort,
    title,
    alignNum,
}: SortableTHProps<K>) {
    const isActive = activeKey === keyName;
    const aria = isActive ? (direction === 'asc' ? 'ascending' : 'descending') : 'none';
    return (
        <th scope="col" className={alignNum ? 'num' : undefined} title={title} aria-sort={aria}>
            <button className={`sortable${isActive ? ' is-active' : ''}`} onClick={() => onSort(keyName)}
                    aria-label={`Sort by ${label}`}>
                {label}{isActive &&
                <span className="sort-indicator" aria-hidden="true">{direction === 'asc' ? '▲' : '▼'}</span>}
            </button>
        </th>
    );
}
