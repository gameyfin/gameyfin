import {
    Alien,
    Baseball,
    Basketball,
    CastleTurret,
    DiceFive,
    GameController,
    Ghost,
    IconContext,
    Joystick,
    Lego,
    Medal,
    PuzzlePiece,
    Rocket,
    Skull,
    SoccerBall,
    Star,
    Strategy,
    Sword,
    Target,
    ThumbsUp,
    TreasureChest,
    Trophy,
    User,
    Volleyball
} from "@phosphor-icons/react";
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
            <GameController className="absolute fill-primary top-[8%] left-[8%] rotate-[350deg]"/>
            <SoccerBall className="absolute fill-primary top-[48%] left-[96%] rotate-[60deg]"/>
            <Joystick className="absolute top-[28%] left-[52%] rotate-[90deg]"/>
            <Strategy className="absolute fill-primary top-[52%] left-[68%] rotate-[30deg]"/>
            <Sword className="absolute top-[72%] left-[12%] rotate-[60deg]"/>
            <Alien className="absolute fill-primary top-[12%] left-[88%] rotate-[15deg]"/>
            <CastleTurret className="absolute top-[6%] left-[38%] rotate-[320deg]"/>
            <Ghost className="absolute fill-primary top-[38%] left-[6%] rotate-[300deg]"/>
            <Skull className="absolute top-[82%] left-[28%] rotate-[90deg]"/>
            <Trophy className="absolute fill-primary top-[12%] left-[62%] rotate-[45deg]"/>
            <Lego className="absolute top-[32%] left-[18%] rotate-[30deg]"/>
            <TreasureChest className="absolute top-[68%] left-[48%] rotate-[75deg]"/>
            <Basketball className="absolute fill-primary top-[22%] left-[37%] rotate-[10deg]"/>
            <Baseball className="absolute top-[92%] left-[82%] rotate-[340deg]"/>
            <DiceFive className="absolute top-[62%] left-[22%] rotate-[120deg]"/>
            <Medal className="absolute fill-primary top-[18%] left-[28%] rotate-[300deg]"/>
            <PuzzlePiece className="absolute top-[42%] left-[78%] rotate-[45deg]"/>
            <Rocket className="absolute fill-primary top-[88%] left-[52%] rotate-[15deg]"/>
            <Star className="absolute top-[28%] left-[72%] rotate-[60deg]"/>
            <Target className="absolute fill-primary top-[68%] left-[62%] rotate-[330deg]"/>
            <ThumbsUp className="absolute top-[82%] left-[12%] rotate-[80deg]"/>
            <User className="absolute fill-primary top-[38%] left-[62%] rotate-[20deg]"/>
            <Volleyball className="absolute top-[78%] left-[92%] rotate-[100deg]"/>
        </IconContext.Provider>
    </div>
}