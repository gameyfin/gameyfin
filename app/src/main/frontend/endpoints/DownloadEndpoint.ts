import {getCsrfToken} from "Frontend/util/auth";
import {toast} from "@heroui/react";

let downloadFrame: HTMLIFrameElement | undefined;

function getDownloadFrame(): HTMLIFrameElement {
    if (!downloadFrame) {
        const frame = document.createElement("iframe");
        frame.hidden = true;
        frame.name = "gameyfin-download";
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
    return downloadFrame;
}

export function downloadGame(gameId: number, provider: string) {
    // Attachments go directly to the download manager without buffering their contents in JavaScript.
    getDownloadFrame().src = `/download/${gameId}?provider=${encodeURIComponent(provider)}&client=browser`;
}

export function downloadSelection(gameId: number, provider: string, selection: { files: string[] } | {
    contentsETag: string
}) {
    const form = document.createElement("form");
    form.method = "post";
    form.action = `/download/${gameId}/selection?client=browser`;
    form.target = getDownloadFrame().name;
    const payload = "files" in selection ? {files: JSON.stringify(selection.files)} : selection;
    for (const [name, value] of Object.entries({provider, ...payload, _csrf: getCsrfToken()})) {
        const input = document.createElement("input");
        input.type = "hidden";
        input.name = name;
        input.value = value;
        form.appendChild(input);
    }
    document.body.appendChild(form);
    try {
        form.submit();
    } finally {
        form.remove();
    }
}
