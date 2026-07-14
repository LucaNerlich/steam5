import type {ReactNode} from "react";
import "@/styles/components/footer.css";

interface FooterShellProps {
    children: ReactNode;
    className?: string;
}

export default function FooterShell({children, className}: Readonly<FooterShellProps>) {
    return (
        <footer className={className}>
            <div className="container">
                {children}
            </div>
        </footer>
    );
}
