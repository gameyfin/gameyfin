import {toast} from "@heroui/react";

let downloadFrame: HTMLIFrameElement | undefined;

export function downloadGame(gameId: number, provider: string) {
    if (!downloadFrame) {
        const frame = document.createElement("iframe");
        frame.hidden = true;
        frame.title = "Downloads";
        frame.onload = () => {
            const result = frame.contentDocument;
            if (result?.URL === "about:blank") return;
            const url = result?.querySelector<HTMLAnchorElement>("a#download-link")?.href;
            frame.src = "about:blank";
            if (!url) {
                toast.danger("Could not start download", {description: "Please try again."});
                return;
            }
            window.open(url, '_top');
            toast.info("Link opened", {
                description: "If nothing happens, you may need to install a compatible client to open this type of link.",
            });
        };
        document.body.appendChild(frame);
        downloadFrame = frame;
    }
    // Attachments go directly to the download manager without buffering their contents in JavaScript.
    downloadFrame.src = `/download/${gameId}?provider=${encodeURIComponent(provider)}&client=browser`;
}
