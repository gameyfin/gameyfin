import { AlienIcon, BaseballIcon, BasketballIcon, CastleTurretIcon, DiceFiveIcon, GameControllerIcon, GhostIcon, IconContext, JoystickIcon, LegoIcon, MedalIcon, PuzzlePieceIcon, RocketIcon, SkullIcon, SoccerBallIcon, StarIcon, StrategyIcon, SwordIcon, TargetIcon, ThumbsUpIcon, TreasureChestIcon, TrophyIcon, UserIcon, VolleyballIcon } from "@phosphor-icons/react";
import React, {useEffect} from "react";

export default function IconBackgroundPattern() {

    const minW = 250;
    const maxW = 1920;
    const minS = 16;
    const maxS = 40;

    const containerRef = React.useRef<HTMLDivElement>(null);
    const [iconSize, setIconSize] = React.useState(minS);

    useEffect(() => {
        const updateSize = () => {
            if (containerRef.current) {
                setIconSize(getResponsiveSize(containerRef.current.offsetWidth));
            }
        };
        updateSize();
        window.addEventListener('resize', updateSize);
        return () => window.removeEventListener('resize', updateSize);
    }, []);

    const getResponsiveSize = (width: number) => {
        const w = Math.max(minW, Math.min(width, maxW));
        return minS + ((w - minW) / (maxW - minW)) * (maxS - minS);
    };

    return <div ref={containerRef} className="absolute w-full h-full opacity-50">
        <IconContext.Provider value={{size: iconSize}}>
            <GameControllerIcon className="absolute fill-primary top-[8%] left-[8%] rotate-350"/>
            <SoccerBallIcon className="absolute fill-primary top-[48%] left-[96%] rotate-60"/>
            <JoystickIcon className="absolute top-[28%] left-[52%] rotate-90"/>
            <StrategyIcon className="absolute fill-primary top-[52%] left-[68%] rotate-30"/>
            <SwordIcon className="absolute top-[72%] left-[12%] rotate-60"/>
            <AlienIcon className="absolute fill-primary top-[12%] left-[88%] rotate-15"/>
            <CastleTurretIcon className="absolute top-[6%] left-[38%] rotate-320"/>
            <GhostIcon className="absolute fill-primary top-[38%] left-[6%] rotate-300"/>
            <SkullIcon className="absolute top-[82%] left-[28%] rotate-90"/>
            <TrophyIcon className="absolute fill-primary top-[12%] left-[62%] rotate-45"/>
            <LegoIcon className="absolute top-[32%] left-[18%] rotate-30"/>
            <TreasureChestIcon className="absolute top-[68%] left-[48%] rotate-75"/>
            <BasketballIcon className="absolute fill-primary top-[22%] left-[37%] rotate-10"/>
            <BaseballIcon className="absolute top-[92%] left-[82%] rotate-340"/>
            <DiceFiveIcon className="absolute top-[62%] left-[22%] rotate-120"/>
            <MedalIcon className="absolute fill-primary top-[18%] left-[28%] rotate-300"/>
            <PuzzlePieceIcon className="absolute top-[42%] left-[78%] rotate-45"/>
            <RocketIcon className="absolute fill-primary top-[88%] left-[52%] rotate-15"/>
            <StarIcon className="absolute top-[28%] left-[72%] rotate-60"/>
            <TargetIcon className="absolute fill-primary top-[68%] left-[62%] rotate-330"/>
            <ThumbsUpIcon className="absolute top-[82%] left-[12%] rotate-80"/>
            <UserIcon className="absolute fill-primary top-[38%] left-[62%] rotate-20"/>
            <VolleyballIcon className="absolute top-[78%] left-[92%] rotate-100"/>
        </IconContext.Provider>
    </div>
}