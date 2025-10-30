import {useEffect, useState} from "react";
import {useSnapshot} from "valtio/react";
import {downloadSessionState} from "Frontend/state/DownloadSessionState";
import {Card, Chip, Tooltip} from "@heroui/react";
import {InfoIcon} from "@phosphor-icons/react";
import {timeUntil} from "Frontend/util/utils";
import {gameState} from "Frontend/state/GameState";

export default function DownloadSessionCard({sessionId}: { sessionId: string }) {
    const session = useSnapshot(downloadSessionState).byId[sessionId];
    const games = useSnapshot(gameState).state;

    // Add state to force continuous re-renders
    const [currentTime, setCurrentTime] = useState(Date.now());

    // Set up an interval to update the time every second
    useEffect(() => {
        const intervalId = setInterval(() => {
            setCurrentTime(Date.now());
        }, 1000);

        // Clean up the interval when component unmounts
        return () => clearInterval(intervalId);
    }, []);

    return (session &&
        <Card
            className={`flex flex-col gap-2 m-0.5 p-2 border-2
            ${(session.currentMbps > 0) ? "border-primary bg-primary/10" : "border-default"}`}>
            <div className="flex flex-row justify-between items-center">
                <p className="flex flex-row items-center">
                    <b>User:</b>&nbsp;
                    {session.username ?? "Anonymous User"}&nbsp;
                    <Tooltip
                        content={<pre>Session ID: {session.sessionId}</pre>}
                        placement="right"
                    >
                        <InfoIcon size={18}/>
                    </Tooltip>
                </p>
                <p>Remote IP:&nbsp;
                    {<Chip size="sm" radius="sm">
                        <pre>{session.remoteIp}</pre>
                    </Chip>}
                </p>
                <p>{session.activeGameIds.length > 0 ? "Session active since" : "Session inactive since"}&nbsp;
                    {<Chip size="sm" radius="sm">
                        {timeUntil(session.startTime, undefined, true)}
                    </Chip>}
                </p>
            </div>
            <div className="flex flex-row gap-2">
                Active downloads:
                {session.activeGameIds.length === 0 && <p>No active downloads</p>}
                {session.activeGameIds.map(gameId =>
                    games[gameId] && <Chip size="sm" radius="sm" key={gameId}>{games[gameId].title}</Chip>
                )}
            </div>
            <p>Current bandwidth: {session.currentMbps.toFixed(1)} Mb/s</p>
        </Card>
    )
}