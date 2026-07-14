import type {ReactNode} from "react";
import HomeFooter from "@/components/footer/HomeFooter";

export default function HomeLayout({children}: {children: ReactNode}) {
    return (
        <>
            {children}
            <HomeFooter/>
        </>
    );
}
