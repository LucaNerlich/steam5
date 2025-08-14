import ThemeToggle from "@/components/ThemeToggle";

export default function Footer() {
    return (
        <footer style={{
            paddingLeft: 'max(12px, env(safe-area-inset-left))',
            paddingRight: 'max(12px, env(safe-area-inset-right))'
        }}>
            <div className="container"
                 style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '1rem 0'}}>
                <small className="text-muted">Imprint: Steam5.org · Luca Nerlich</small>
                <ThemeToggle/>
            </div>
        </footer>
    );
}


