import React, {useMemo} from "react";
import {useSnapshot} from "valtio/react";
import type PluginConfigMetadataDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/PluginConfigMetadataDto";
import PluginConfigFormField from "Frontend/components/general/plugin/PluginConfigFormField";
import AutocompleteInput from "Frontend/components/general/input/AutocompleteInput";
import {gameState} from "Frontend/state/GameState";
import {libraryState} from "Frontend/state/LibraryState";

const GAME_SELECTION = "gameselection";

function isGameSelection(meta: PluginConfigMetadataDto) {
    return meta.type.toLowerCase() === GAME_SELECTION;
}

/**
 * Converts the string representation of a task setting or parameter into the value used by the form field.
 */
export function toTaskFieldValue(meta: PluginConfigMetadataDto, value: unknown): unknown {
    const type = meta.type.toLowerCase();
    if (type === GAME_SELECTION) {
        if (value == null) return [];
        return value.toString().split(",").map(id => id.trim()).filter(id => id.length > 0);
    }
    if (value == null) return type === "boolean" ? false : "";
    switch (type) {
        case "float":
        case "int":
            return Number(value);
        case "boolean":
            return value === true || value === "true";
        default:
            return value.toString();
    }
}

/**
 * Converts the value of a form field back into the string representation expected by the backend.
 */
export function fromTaskFieldValue(meta: PluginConfigMetadataDto, value: unknown): string | undefined {
    if (value == null || value === "") return undefined;
    if (Array.isArray(value)) return value.length > 0 ? value.join(",") : undefined;
    return String(value);
}

/**
 * Returns an error message if a required value is missing.
 */
export function validateTaskFieldValue(meta: PluginConfigMetadataDto, value: unknown): string | undefined {
    if (!meta.required) return undefined;
    const missing = value == null || value === "" || (Array.isArray(value) && value.length === 0);
    return missing ? `${meta.label} is required` : undefined;
}

function GameSelectionField({meta, name}: { meta: PluginConfigMetadataDto, name: string }) {
    const games = useSnapshot(gameState).sortedAlphabetically;
    const libraries = useSnapshot(libraryState).state;
    const multipleLibraries = Object.keys(libraries).length > 1;

    const options = useMemo(() => games.map(game => ({
        id: String(game.id),
        label: multipleLibraries && libraries[game.libraryId]
            ? `${game.title} (${libraries[game.libraryId].name})`
            : game.title
    })), [games, libraries, multipleLibraries]);

    return (
        <div className="flex flex-col">
            <AutocompleteInput label={meta.label}
                               name={name}
                               options={options}
                               placeholder="Select games..."/>
            {meta.description && <p className="text-xs text-muted -mt-5 mb-2">{meta.description}</p>}
        </div>
    );
}

/**
 * Form field for a task setting or run parameter.
 * Supports all types of plugin configurations and additionally game selections.
 */
export default function TaskConfigFormField({meta, name}: { meta: PluginConfigMetadataDto, name: string }) {
    if (isGameSelection(meta)) {
        return <GameSelectionField meta={meta} name={name}/>;
    }
    return (
        <PluginConfigFormField pluginConfigMetadata={meta}
                               name={name}
                               description={meta.description}
                               variant="secondary"/>
    );
}
