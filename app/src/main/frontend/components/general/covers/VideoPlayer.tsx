import type {ComponentProps} from "react";
import ReactPlayerModule from "react-player";
import {PlayIcon} from "@phosphor-icons/react";

// Depending on the bundler interop the default import may be the module namespace.
const ReactPlayer = ((ReactPlayerModule as any).default ?? ReactPlayerModule) as typeof ReactPlayerModule;

export function canPlayVideo(url: string) {
    try {
        return ["http:", "https:"].includes(new URL(url).protocol) && !!ReactPlayer.canPlay?.(url);
    } catch {
        return false;
    }
}

export default function VideoPlayer(props: ComponentProps<typeof ReactPlayer>) {
    return <ReactPlayer width="100%" height="100%" light controls
                        playIcon={<PlayIcon weight="fill"/>}
                        config={{
                            youtube: {
                                // Apply the policy to the iframe, not the ReactPlayer wrapper.
                                referrerpolicy: "strict-origin-when-cross-origin",
                                origin: window.location.origin
                            }
                        }}
                        {...props}/>;
}
