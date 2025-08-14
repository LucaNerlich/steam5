import ThemeToggle from "@/components/ThemeToggle";
import "@/styles/footer.css";

export default function Footer() {
    return (
        <footer>
            <div className="container">
                <small className="text-muted">Imprint: Steam5.org · Luca Nerlich</small>
                <ThemeToggle/>
            </div>
        </footer>
    );
}


