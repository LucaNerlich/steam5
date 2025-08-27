export default function Loading() {
    return (
        <section className="container">
            <section className="profile">
                <div className="profile__info">
                    <div style={{width: 56, height: 56, borderRadius: '50%', background: 'var(--color-border)'}}/>
                    <div style={{display: 'grid', gap: 8}}>
                        <div style={{width: 180, height: 20, background: 'var(--color-border)'}}/>
                        <div style={{width: 120, height: 14, background: 'var(--color-border)'}}/>
                    </div>
                </div>
                <div style={{display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8}}>
                    {Array.from({length: 6}).map((_, i) => (
                        <div key={i} style={{height: 40, background: 'var(--color-border)'}}/>
                    ))}
                </div>
                <div style={{height: 160, background: 'var(--color-border)', borderRadius: 8}}/>
            </section>
        </section>
    );
}


