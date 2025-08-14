import ThemeToggle from "@/components/ThemeToggle";
import FontToggle from "./FontToggle";
import ResetTodayButton from "./ResetTodayButton";
import "@/styles/components/footer.css";

export default function Footer() {
    return (
        <footer>
            <div className="container">
                <small className="text-muted">Imprint: Steam5.org · Luca Nerlich</small>
                <div className="controls">
                    <ThemeToggle/>
                    <FontToggle/>
                    <ResetTodayButton/>
                </div>
            </div>
        </footer>
    );
}


