import Link from "next/link";
import Image from "next/image";
import {Routes} from "../../app/routes";

export default function Header() {
    return (
        <header>
            <div className="container"
                 style={{display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '1rem 0'}}>
                <Link href={Routes.home} className="brand"
                      style={{display: 'inline-flex', gap: '8px', alignItems: 'center'}}>
                    <Image src="/logo.svg" alt="Steam5" width={20} height={20} priority/>
                    <span>Steam5</span>
                </Link>
                <Link href={Routes.reviewGuesser} className="btn">Play Review Guesser</Link>
            </div>
        </header>
    );
}


