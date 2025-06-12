import {Image, Tooltip} from "@heroui/react";
import {Plug} from "@phosphor-icons/react";
import {initializePluginState, pluginState} from "Frontend/state/PluginState";
import {useSnapshot} from "valtio/react";
import {useEffect} from "react";

interface PluginLogoProps {
    pluginId: string;
}

export default function PluginIcon({pluginId}: PluginLogoProps) {
    const state = useSnapshot(pluginState);

    useEffect(() => {
        initializePluginState();
    }, []);

    return state.isLoaded && (
        <Tooltip content={state.state[pluginId].name}>
            {state.state[pluginId].hasLogo ?
                <Image src={`/images/plugins/${state.state[pluginId].id}/logo`} width={16} height={16} radius="none"/> :
                <Plug size={16} weight="fill"/>
            }
        </Tooltip>
    )
}