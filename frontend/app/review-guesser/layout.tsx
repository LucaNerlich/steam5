import type {ReactNode} from "react";
import ReviewGuesserFooter from "@/components/footer/ReviewGuesserFooter";

export default function ReviewGuesserLayout({children}: {children: ReactNode}) {
    return (
        <>
            {children}
            <ReviewGuesserFooter/>
        </>
    );
}
