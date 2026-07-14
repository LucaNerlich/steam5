import type {ReactNode} from "react";
import YearGuesserFooter from "@/components/footer/YearGuesserFooter";

export default function YearGuesserLayout({children}: {children: ReactNode}) {
    return (
        <>
            {children}
            <YearGuesserFooter/>
        </>
    );
}
