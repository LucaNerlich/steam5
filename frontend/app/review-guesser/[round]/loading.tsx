import styles from "./loading.module.css";

export default function Loading() {
    return (
        <section className={`container ${styles.loading}`}>
            <div className={styles.spacerLg}/>

            <div className={styles.heroMeta}>
                <div className={styles.titleBar}/>
                <div className={styles.subtitleBar}/>
                <div className={styles.metaRow}>
                    {Array.from({length: 4}).map((_, i) => (
                        <div key={i} className={styles.metaItem}/>
                    ))}
                </div>
                <div className={styles.pillRow}>
                    {Array.from({length: 4}).map((_, i) => (
                        <div key={i} className={styles.pill}/>
                    ))}
                </div>
            </div>

            <div className={styles.spacerLg}/>

            <div className={styles.shots}>
                {Array.from({length: 4}).map((_, i) => (
                    <div key={i} className={styles.shot}/>
                ))}
            </div>

            <div className={styles.spacerLg}/>

            <div className={styles.afterShots}>
                <div className={styles.desktopCard}/>
                <div className={styles.guessSection}>
                    <div className={styles.subhead}/>
                    <div className={styles.spacerSm}/>
                    <div className={styles.guessList}>
                        {Array.from({length: 5}).map((_, i) => (
                            <div key={i} className={styles.guessItem}/>
                        ))}
                    </div>
                </div>
                <div className={styles.summaryCard}/>
            </div>
        </section>
    );
}


