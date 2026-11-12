import Popup from "Frontend/components/general/Popup";
import {KeyIcon, UserCircleIcon} from "@phosphor-icons/react";
import {useSnapshot} from "valtio/react";
import {GameFieldMetadataDto, GameFieldMetadataType} from "Frontend/dtos/GameDtos";
import PluginIcon from "Frontend/components/general/plugin/PluginIcon";
import {pluginState} from "Frontend/state/PluginState";

// Tooltip triggers default to inline-block, which adds baseline space below small icons.
const sourceIconClassName = "ml-2 inline-flex shrink-0 items-center self-center align-middle cursor-default [&_[data-slot=tooltip-trigger]]:inline-flex";

export default function GameMetadataSourceIcon({source}: { source?: GameFieldMetadataDto }) {
    const plugins = useSnapshot(pluginState).state;
    if (!source?.source || source.type === GameFieldMetadataType.UNKNOWN) return null;

    if (source.type === GameFieldMetadataType.PLUGIN) {
        const plugin = plugins[source.source] ?? {id: source.source, name: source.sourceName || source.source};
        return <span className={sourceIconClassName}>
            <PluginIcon plugin={plugin} size={14} showTooltip={true}/>
        </span>;
    }

    if (source.type !== GameFieldMetadataType.USER && source.type !== GameFieldMetadataType.API_KEY) return null;

    const Icon = source.type === GameFieldMetadataType.USER ? UserCircleIcon : KeyIcon;
    const description = source.type === GameFieldMetadataType.API_KEY ? `API key: ${source.source}` : source.source;

    return <span className={sourceIconClassName}>
        <Popup>
            <Popup.Trigger aria-label={description} className="inline-flex items-center cursor-default">
                <Icon size={14} weight="fill"/>
            </Popup.Trigger>
            <Popup.Content>{description}</Popup.Content>
        </Popup>
    </span>;
}
