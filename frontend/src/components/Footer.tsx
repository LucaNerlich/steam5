import ThemeToggle from "@/components/ThemeToggle";
import FontToggle from "./FontToggle";
import "@/styles/footer.css";

export default function Footer() {
    return (
        <footer>
            <div className="container">
                <small className="text-muted">Imprint: Steam5.org · Luca Nerlich</small>
                <div className="controls">
                    <ThemeToggle/>
                    <FontToggle/>
                </div>
            </div>
        </footer>
    );
}


