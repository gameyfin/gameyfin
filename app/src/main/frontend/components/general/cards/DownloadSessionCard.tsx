import {useSnapshot} from "valtio/react";
import {downloadSessionState} from "Frontend/state/DownloadSessionState";
import {Card, Chip, Tooltip} from "@heroui/react";
import {InfoIcon} from "@phosphor-icons/react";
import {convertBpsToMbps, hslToHex, humanFileSize, timeUntil} from "Frontend/util/utils";
import {gameState} from "Frontend/state/GameState";
import RealtimeChart, {RealtimeChartData, RealtimeChartOptions} from "react-realtime-chart";
import {useEffect, useState} from "react";
import {useNavigate} from "react-router";
import {libraryState} from "Frontend/state/LibraryState";

export function DownloadSessionCard({sessionId}: { sessionId: string }) {
    const navigate = useNavigate();

    const session = useSnapshot(downloadSessionState).byId[sessionId];
    const games = useSnapshot(gameState).state;
    const libraries = useSnapshot(libraryState).state;

    const [currentTime, setCurrentTime] = useState<Date>(new Date());
    const [chartData, setChartData] = useState<RealtimeChartData[][]>([]);
    const [foregroundColor, setForegroundColor] = useState<string>("#00F");

    // Get theme colors from CSS variables
    useEffect(() => {
        const chartColor = window.getComputedStyle(document.body).getPropertyValue('--heroui-foreground');
        if (chartColor) {
            setForegroundColor(hslToHex(chartColor.trim()));
        }
    }, []);

    useEffect(() => {
        const interval = setInterval(() => {
            setCurrentTime(new Date());
        }, 1000);
        return () => clearInterval(interval);
    }, []);

    useEffect(() => {
        if (session) {
            const dataPoints: RealtimeChartData[] = session.bandwidthHistory.map((bps, idx) => {
                let date = new Date();
                date.setSeconds(currentTime.getSeconds() - session.bandwidthHistory.length + idx + 1);
                return {
                    date: date,
                    value: convertBpsToMbps(bps)
                };
            });
            setChartData([dataPoints]);
        }
    }, [currentTime]);

    const chartOptions: RealtimeChartOptions = {
        fps: 60,
        timeSlots: 30,
        colors: [foregroundColor],
        margin: {left: 60},
        lines: [
            {
                area: true,
                areaColor: foregroundColor,
                areaOpacity: 0.03,
                lineWidth: 2,
                curve: "basis",
            },
        ],
        yGrid: {
            min: 0,
            color: foregroundColor,
            opacity: 0.25,
            size: 1,
            tickNumber: 7,
            tickFormat: (v) => `${v}Mb/s`
        },
        xGrid: {
            color: foregroundColor,
            opacity: 0.25,
            size: 1,
            tickNumber: 5
        },
    };

    return (session &&
        <Card
            className={`flex flex-col gap-2 m-0.5 p-4 border-2
            ${(session.currentBytesPerSecond > 0) ? "border-primary bg-primary/10" : "border-default"}`}>
            <div className="flex flex-row items-center">
                <p className="flex flex-row items-center flex-1">
                    <b>User:</b>&nbsp;
                    {session.username ?? "Anonymous User"}&nbsp;
                    <Tooltip
                        content={<pre>Session ID: {session.sessionId}</pre>}
                        placement="right"
                    >
                        <InfoIcon size={18}/>
                    </Tooltip>
                </p>
                <div className="flex-1 flex justify-center">Remote IP:&nbsp;
                    {<Chip size="sm" radius="sm">
                        <pre>{session.remoteIp}</pre>
                    </Chip>}
                </div>
                <div
                    className="flex-1 flex justify-end">{session.activeGameIds.length > 0 ? "Session active since" : "Session inactive since"}&nbsp;
                    {<Chip size="sm" radius="sm">
                        {timeUntil(session.startTime, undefined, true)}
                    </Chip>}
                </div>
            </div>
            {/* Only render chart when downloads are active or have been active within the last minute */}
            {(session.activeGameIds.length > 0 || (currentTime.getTime() - new Date(session.startTime).getTime() < 60000)) &&
                <div className="flex flex-col items-center">
                    <div className="flex flex-row gap-2">
                        Active downloads:
                        {session.activeGameIds.length === 0 && <p>No active downloads</p>}
                        {session.activeGameIds.map(gameId =>
                            games[gameId] &&
                            <Tooltip key={gameId}
                                     size="sm"
                                     content={`Size: ${humanFileSize(games[gameId].metadata.fileSize)} / Library: ${libraries[games[gameId].libraryId]?.name || "Unknown"}`}
                                     placement="bottom">
                                <Chip size="sm" radius="sm"
                                      onClick={() => navigate(`/game/${gameId}`)}
                                      className="cursor-pointer"
                                >{games[gameId].title}
                                </Chip>
                            </Tooltip>
                        )}
                    </div>
                    <div className="w-full h-48">
                        <RealtimeChart options={chartOptions} data={chartData}/>
                    </div>
                </div>
            }
        </Card>
    )
}