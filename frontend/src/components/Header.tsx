import Link from "next/link";
import Image from "next/image";
import {Routes} from "../../app/routes";
import "@/styles/components/header.css";
import SteamLoginButton from "@/components/SteamLoginButton";

export default function Header() {
    return (
        <header>
            <div className="container">
                <Link href={Routes.home} className="brand">
                    <Image src="/logo.svg" alt="Steam5" width={20} height={20} priority/>
                    <span>Steam5</span>
                </Link>
                <SteamLoginButton/>
            </div>
        </header>
    );
}


