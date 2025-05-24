import {Input, Select, SelectItem} from "@heroui/react";
import {MagnifyingGlass} from "@phosphor-icons/react";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import {libraryState} from "Frontend/state/LibraryState";
import {useSearchParams} from "react-router";
import {useEffect, useMemo, useState} from "react";
import {Fzf} from "fzf";
import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryDto";
import CoverGrid from "Frontend/components/general/covers/CoverGrid";
import {toTitleCase} from "Frontend/util/utils";

export default function SearchView() {
    const games = useSnapshot(gameState).sortedAlphabetically as GameDto[];
    const knownDevelopers = useSnapshot(gameState).knownDevelopers as Set<string>;
    const knownGenres = useSnapshot(gameState).knownGenres;
    const knownThemes = useSnapshot(gameState).knownThemes;
    const knownFeatures = useSnapshot(gameState).knownFeatures;
    const knownPerspectives = useSnapshot(gameState).knownPerspectives;
    const libraries = useSnapshot(libraryState).libraries as LibraryDto[];

    const [searchParams, setSearchParams] = useSearchParams();
    const [initialLoadComplete, setInitialLoadComplete] = useState(false);

    // State to track selected filter values
    const [searchTerm, setSearchTerm] = useState<string>("");
    const [selectedLibraries, setSelectedLibraries] = useState<Set<string>>(new Set());
    const [selectedDevelopers, setSelectedDevelopers] = useState<Set<string>>(new Set());
    const [selectedGenres, setSelectedGenres] = useState<Set<string>>(new Set());
    const [selectedThemes, setSelectedThemes] = useState<Set<string>>(new Set());
    const [selectedFeatures, setSelectedFeatures] = useState<Set<string>>(new Set());
    const [selectedPerspectives, setSelectedPerspectives] = useState<Set<string>>(new Set());

    // Load initial filter values from URL parameters on component mount
    useEffect(() => {
        // Get all parameters from the URL
        const term = searchParams.get("term") || "";
        const libs = searchParams.getAll("lib");
        const devs = searchParams.getAll("dev");
        const genres = searchParams.getAll("genre");
        const themes = searchParams.getAll("theme");
        const features = searchParams.getAll("feature");
        const perspectives = searchParams.getAll("perspective");

        setSearchTerm(term);
        setSelectedLibraries(new Set(libs));
        setSelectedDevelopers(new Set(devs));
        setSelectedGenres(new Set(genres));
        setSelectedThemes(new Set(themes));
        setSelectedFeatures(new Set(features));
        setSelectedPerspectives(new Set(perspectives));

        setInitialLoadComplete(true);
    }, []);

    // Update search parameters whenever the filters change
    useEffect(() => {
        if (!initialLoadComplete) return;

        const newParams = new URLSearchParams();

        // Preserve search term if exists
        if (searchTerm && searchTerm.trim() !== "") {
            newParams.set("term", searchTerm);
        }

        // Only add parameters for non-empty filters
        if (selectedLibraries.size > 0) {
            selectedLibraries.forEach(lib => {
                newParams.append("lib", lib.toString());
            });
        }

        if (selectedDevelopers.size > 0) {
            selectedDevelopers.forEach(dev => {
                newParams.append("dev", dev);
            });
        }

        if (selectedGenres.size > 0) {
            selectedGenres.forEach(genre => {
                newParams.append("genre", genre);
            });
        }

        if (selectedThemes.size > 0) {
            selectedThemes.forEach(theme => {
                newParams.append("theme", theme);
            });
        }

        if (selectedFeatures.size > 0) {
            selectedFeatures.forEach(feature => {
                newParams.append("feature", feature);
            });
        }

        if (selectedPerspectives.size > 0) {
            selectedPerspectives.forEach(perspective => {
                newParams.append("perspective", perspective);
            });
        }

        setSearchParams(newParams, {replace: true});
    }, [searchTerm, selectedLibraries, selectedDevelopers, selectedGenres,
        selectedThemes, selectedFeatures, selectedPerspectives]);

    const filteredGames = useMemo(() => filterGames(), [
        games, searchTerm,
        selectedLibraries, selectedDevelopers,
        selectedGenres, selectedThemes,
        selectedFeatures, selectedPerspectives
    ]);

    function filterGames(): GameDto[] {
        let filtered = games;

        // Apply text search filter if term exists
        if (searchTerm !== "") {
            const fzf = new Fzf(filtered, {
                selector: (game: GameDto) => game.title
            });
            filtered = fzf.find(searchTerm).map(result => result.item);
        }

        // Apply library filter
        if (selectedLibraries.size > 0) {
            filtered = filtered.filter(game => selectedLibraries.has(game.libraryId.toString()));
        }

        // Apply developer filter
        if (selectedDevelopers.size > 0) {
            filtered = filtered.filter(game =>
                game.developers?.some(developer => selectedDevelopers.has(developer))
            );
        }

        // Apply genre filter
        if (selectedGenres.size > 0) {
            filtered = filtered.filter(game =>
                game.genres?.some(genre => selectedGenres.has(genre))
            );
        }

        // Apply theme filter
        if (selectedThemes.size > 0) {
            filtered = filtered.filter(game =>
                game.themes?.some(theme => selectedThemes.has(theme))
            );
        }

        // Apply feature filter
        if (selectedFeatures.size > 0) {
            filtered = filtered.filter(game =>
                game.features?.some(feature => selectedFeatures.has(feature))
            );
        }

        // Apply perspective filter
        if (selectedPerspectives.size > 0) {
            filtered = filtered.filter(game =>
                game.perspectives?.some(perspective => selectedPerspectives.has(perspective))
            );
        }

        return filtered;
    }

    return <div className="flex flex-col gap-4 items-center w-full">
        <Input
            classNames={{
                base: "w-1/3",
                mainWrapper: "h-full",
                inputWrapper:
                    "h-full font-normal text-default-500 bg-default-400/20 dark:bg-default-500/20",
            }}
            placeholder="Type to search..."
            startContent={<MagnifyingGlass/>}
            type="search"
            value={searchTerm}
            isClearable
            onChange={(event) => setSearchTerm(event.target.value)}
            onClear={() => setSearchTerm("")}
        />
        <div className="flex flex-row flex-wrap gap-2 justify-center">
            <Select
                size="sm"
                className="max-w-xs"
                selectionMode="multiple"
                label="Libraries"
                placeholder="Filter by library"
                selectedKeys={selectedLibraries}
                //@ts-ignore
                onSelectionChange={setSelectedLibraries}
            >
                {libraries.map((library) => (
                    <SelectItem key={library.id}>{library.name}</SelectItem>
                ))}
            </Select>
            <Select
                size="sm"
                className="max-w-xs"
                selectionMode="multiple"
                label="Developers"
                placeholder="Filter by developer"
                selectedKeys={selectedDevelopers}
                //@ts-ignore
                onSelectionChange={setSelectedDevelopers}
            >
                {Array.from(knownDevelopers).map((developer) => (
                    <SelectItem key={developer}>{developer}</SelectItem>
                ))}
            </Select>
            <Select
                size="sm"
                className="max-w-xs"
                selectionMode="multiple"
                label="Genres"
                placeholder="Filter by genre"
                selectedKeys={selectedGenres}
                //@ts-ignore
                onSelectionChange={setSelectedGenres}
            >
                {Array.from(knownGenres).map((genre) => (
                    <SelectItem key={genre}>{toTitleCase(genre)}</SelectItem>
                ))}
            </Select>
            <Select
                size="sm"
                className="max-w-xs"
                selectionMode="multiple"
                label="Themes"
                placeholder="Filter by theme"
                selectedKeys={selectedThemes}
                //@ts-ignore
                onSelectionChange={setSelectedThemes}
            >
                {Array.from(knownThemes).map((theme) => (
                    <SelectItem key={theme}>{toTitleCase(theme)}</SelectItem>
                ))}
            </Select>
            <Select
                size="sm"
                className="max-w-xs"
                selectionMode="multiple"
                label="Features"
                placeholder="Filter by feature"
                selectedKeys={selectedFeatures}
                //@ts-ignore
                onSelectionChange={setSelectedFeatures}
            >
                {Array.from(knownFeatures).map((feature) => (
                    <SelectItem key={feature}>{toTitleCase(feature)}</SelectItem>
                ))}
            </Select>
            <Select
                size="sm"
                className="max-w-xs"
                selectionMode="multiple"
                label="Perspectives"
                placeholder="Filter by perspective"
                selectedKeys={selectedPerspectives}
                //@ts-ignore
                onSelectionChange={setSelectedPerspectives}
            >
                {Array.from(knownPerspectives).map((perspective) => (
                    <SelectItem key={perspective}>{toTitleCase(perspective)}</SelectItem>
                ))}
            </Select>
        </div>
        <div className="mt-4 w-full px-4 select-none">
            <CoverGrid games={filteredGames}/>
            {filteredGames.length === 0 && (
                <div className="text-center mt-8 text-default-500">
                    No games found matching your filters
                </div>
            )}
        </div>
    </div>
}