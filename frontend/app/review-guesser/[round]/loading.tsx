export default function Loading() {
    return (
        <section className="container">
            <div style={{height: 24}}/>

            <div style={{display: 'flex', flexDirection: 'column', gap: 12}}>
                <div style={{height: 28, width: 280, background: 'var(--color-border)', borderRadius: 8}}/>
                <div style={{height: 18, width: 100, background: 'var(--color-border)', borderRadius: 6}}/>
                <div style={{display: 'flex', gap: 8}}>
                    {Array.from({length: 4}).map((_, i) => (
                        <div key={i} style={{height: 18, width: 70, background: 'var(--color-border)', borderRadius: 6}}/>
                    ))}
                </div>
                <div style={{display: 'flex', gap: 8}}>
                    {Array.from({length: 4}).map((_, i) => (
                        <div key={i} style={{height: 22, width: 72, background: 'var(--color-border)', borderRadius: 999}}/>
                    ))}
                </div>
            </div>

            <div style={{height: 24}}/>

            <div style={{display: 'grid', gap: 8, gridTemplateColumns: 'repeat(2, minmax(0, 1fr))'}}>
                {Array.from({length: 4}).map((_, i) => (
                    <div
                        key={i}
                        style={{
                            aspectRatio: '16 / 9',
                            background: 'var(--color-border)',
                            borderRadius: 8,
                            width: '100%',
                            height: 'auto',
                            maxHeight: 225,
                        }}
                    />
                ))}
            </div>

            <div style={{height: 24}}/>

            <div style={{height: 20, width: 160, background: 'var(--color-border)', borderRadius: 6}}/>
            <div style={{height: 12}}/>
            <div style={{display: 'grid', gap: 8, gridTemplateColumns: '1fr'}}>
                {Array.from({length: 5}).map((_, i) => (
                    <div key={i} style={{height: 44, background: 'var(--color-border)', borderRadius: 8}}/>
                ))}
            </div>

            <div style={{height: 24}}/>
            <div style={{height: 190, background: 'var(--color-border)', borderRadius: 8}}/>
        </section>
    );
}


