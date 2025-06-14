import {
    Alien,
    CastleTurret,
    GameController,
    Ghost,
    Joystick,
    Lego,
    Skull,
    SoccerBall,
    Strategy,
    Sword,
    TreasureChest,
    Trophy
} from "@phosphor-icons/react";
import React from "react";

export default function IconBackgroundPattern() {
    return <div className="absolute w-full h-full opacity-50">
        <GameController size={32} className="absolute fill-primary top-[10%] left-[10%] rotate-[350deg]"/>
        <SoccerBall size={34} className="absolute fill-primary top-[50%] left-[35%] rotate-[60deg]"/>
        <Joystick size={40} className="absolute top-[30%] left-[50%] rotate-[90deg]"/>
        <Strategy size={36} className="absolute fill-primary top-[50%] left-[70%] rotate-[30deg]"/>
        <Sword size={28} className="absolute top-[70%] left-[10%] rotate-[60deg]"/>
        <Alien size={34} className="absolute fill-primary top-[10%] left-[85%] rotate-[15deg]"/>
        <CastleTurret size={30} className="absolute top-[5%] left-[40%] rotate-[320deg]"/>
        <Ghost size={38} className="absolute fill-primary top-[40%] left-[5%] rotate-[300deg]"/>
        <Skull size={32} className="absolute top-[80%] left-[30%] rotate-[90deg]"/>
        <Trophy size={36} className="absolute fill-primary top-[10%] left-[60%] rotate-[45deg]"/>
        <Lego size={28} className="absolute top-[30%] left-[20%] rotate-[30deg]"/>
        <TreasureChest size={40} className="absolute top-[70%] left-[50%] rotate-[75deg]"/>
    </div>
}