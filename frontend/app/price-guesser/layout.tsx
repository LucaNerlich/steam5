import type {ReactNode} from "react";
import PriceGuesserFooter from "@/components/footer/PriceGuesserFooter";

export default function PriceGuesserLayout({children}: {children: ReactNode}) {
    return (
        <>
            {children}
            <PriceGuesserFooter/>
        </>
    );
}
