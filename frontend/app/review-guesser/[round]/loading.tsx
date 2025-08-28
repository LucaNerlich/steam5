export default function Loading() {
    return (
        <section className="container">
            <div style={{height: 48}}/>
            <div style={{height: 180, background: 'var(--color-border)', borderRadius: 8}}/>
            <div style={{height: 16}}/>
            <div style={{display: 'grid', gap: 8, gridTemplateColumns: 'repeat(5, minmax(0, 1fr))'}}>
                {Array.from({length: 5}).map((_, i) => (
                    <div key={i} style={{height: 44, background: 'var(--color-border)', borderRadius: 8}}/>
                ))}
            </div>
            <div style={{height: 24}}/>
            <div style={{height: 120, background: 'var(--color-border)', borderRadius: 8}}/>
        </section>
    );
}


