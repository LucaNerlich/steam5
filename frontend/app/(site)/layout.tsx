import type {ReactNode} from "react";
import HomeFooter from "@/components/footer/HomeFooter";

export default function SiteLayout({children}: {children: ReactNode}) {
    return (
        <>
            {children}
            <HomeFooter/>
        </>
    );
}
