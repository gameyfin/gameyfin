import {Button, Input, Select, SelectedItems, SelectItem, Tooltip} from "@heroui/react";
import {
    FunnelSimpleIcon,
    FunnelSimpleXIcon,
    MagnifyingGlassIcon,
    SortAscendingIcon,
    StarIcon
} from "@phosphor-icons/react";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import {libraryState} from "Frontend/state/LibraryState";
import {useSearchParams} from "react-router";
import React, {useEffect, useMemo, useState} from "react";
import {Fzf} from "fzf";
import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import LibraryDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryDto";
import CoverGrid from "Frontend/components/general/covers/CoverGrid";
import {compoundRating, toTitleCase} from "Frontend/util/utils";

export default function SearchView() {
    const games = useSnapshot(gameState).sortedAlphabetically as GameDto[];
    const knownDevelopers = useSnapshot(gameState).knownDevelopers as Set<string>;
    const knownGenres = useSnapshot(gameState).knownGenres;
    const knownThemes = useSnapshot(gameState).knownThemes;
    const knownFeatures = useSnapshot(gameState).knownFeatures;
    const knownPerspectives = useSnapshot(gameState).knownPerspectives;
    const knownKeywords = useSnapshot(gameState).knownKeywords;
    const libraries = useSnapshot(libraryState).libraries as LibraryDto[];

    const [searchParams, setSearchParams] = useSearchParams();
    const [initialLoadComplete, setInitialLoadComplete] = useState(false);

    const [showFilters, setShowFilters] = useState(false);
    const [sortBy, setSortBy] = useState("title_asc");

    // State to track selected filter values
    const [searchTerm, setSearchTerm] = useState<string>("");
    const [selectedLibraries, setSelectedLibraries] = useState<Set<string>>(new Set());
    const [selectedDevelopers, setSelectedDevelopers] = useState<Set<string>>(new Set());
    const [selectedGenres, setSelectedGenres] = useState<Set<string>>(new Set());
    const [selectedThemes, setSelectedThemes] = useState<Set<string>>(new Set());
    const [selectedFeatures, setSelectedFeatures] = useState<Set<string>>(new Set());
    const [selectedPerspectives, setSelectedPerspectives] = useState<Set<string>>(new Set());
    const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set());
    const [minRating, setMinRating] = useState<number>(1); // Minimum rating filter

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
        const keywords = searchParams.getAll("keyword");
        const sort = searchParams.get("sort") || "title_asc";
        const minRatingParam = parseInt(searchParams.get("minRating") || "1", 10);
        const filtersParam = searchParams.get("filters");

        setSearchTerm(term);
        setSelectedLibraries(new Set(libs));
        setSelectedDevelopers(new Set(devs));
        setSelectedGenres(new Set(genres));
        setSelectedThemes(new Set(themes));
        setSelectedFeatures(new Set(features));
        setSelectedPerspectives(new Set(perspectives));
        setSelectedKeywords(new Set(keywords));
        setSortBy(sort);
        setMinRating(isNaN(minRatingParam) ? 1 : minRatingParam);
        setShowFilters(filtersParam === "1");

        setInitialLoadComplete(true);
    }, []);

    // Update search parameters whenever the filters or sort change
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
        if (selectedKeywords.size > 0) {
            selectedKeywords.forEach(keyword => {
                newParams.append("keyword", keyword);
            });
        }
        // Add minRating param if not default
        if (minRating > 1) {
            newParams.set("minRating", minRating.toString());
        }
        // Add sort param
        if (sortBy && sortBy !== "title_asc") {
            newParams.set("sort", sortBy);
        }
        // Add showFilters param
        if (showFilters) {
            newParams.set("filters", "1");
        }

        setSearchParams(newParams, {replace: true});
    }, [searchTerm, selectedLibraries, selectedDevelopers, selectedGenres,
        selectedThemes, selectedFeatures, selectedPerspectives, selectedKeywords, sortBy, minRating, showFilters]);

    // Sorting function (refactored to use sortKey and sortDirection)
    function sortGames(games: GameDto[]): GameDto[] {
        if (!sortBy) return games;

        const [sortKey, sortDirection] = sortBy.split("_");

        return games.slice().sort((a, b) => {
            let cmp: number;

            switch (sortKey) {
                case "title":
                    cmp = a.title.localeCompare(b.title);
                    break;
                case "release":
                    cmp = (a.release || "").localeCompare(b.release || "");
                    break;
                case "rating":
                    cmp = compoundRating(a) - compoundRating(b);
                    break;
                case "added":
                    cmp = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
                    break;
                case "updated":
                    cmp = new Date(a.updatedAt).getTime() - new Date(b.updatedAt).getTime();
                    break;
                default:
                    cmp = 0;
            }
            if (sortDirection === "desc") {
                cmp *= -1; // Reverse the comparison if sorting in descending order
            }
            return cmp;
        });
    }

    const filteredAndSortedGames = useMemo(() => sortGames(filterGames()), [
        games, searchTerm,
        selectedLibraries, selectedDevelopers,
        selectedGenres, selectedThemes,
        selectedFeatures, selectedPerspectives, selectedKeywords, sortBy, minRating
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

        // Apply keyword filter
        if (selectedKeywords.size > 0) {
            filtered = filtered.filter(game =>
                game.keywords?.some(keyword => selectedKeywords.has(keyword))
            );
        }

        // Apply minimum rating filter
        if (minRating > 1) {
            filtered = filtered.filter(game => {
                const starRating = compoundRating(game, [1, 5]);
                if (minRating === 5) {
                    return starRating > 4.5;
                }
                return starRating >= minRating;
            });
        }
        return filtered;
    }

    function stars(filled: number, total: number = 5) {
        const stars = [];
        for (let i = 0; i < total; i++) {
            stars.push(
                <StarIcon key={i} weight={i < filled ? "fill" : "regular"} className="inline-block"/>
            );
        }
        return <div className="flex flex-row">
            {stars}
        </div>;
    }

    return <div className="flex flex-col gap-4 items-center w-full">
        <div className="flex w-full justify-between px-12 gap-4 flex-col lg:flex-row">
            <Input
                classNames={{
                    base: "w-full lg:w-96 shrink-0",
                    mainWrapper: "h-full",
                    inputWrapper:
                        "h-full font-normal text-default-500 bg-default-400/20 dark:bg-default-500/20",
                }}
                placeholder="Type to search..."
                startContent={<MagnifyingGlassIcon/>}
                type="search"
                value={searchTerm}
                isClearable
                onChange={(event) => setSearchTerm(event.target.value)}
                onClear={() => setSearchTerm("")}
            />
            <div className="flex flex-row gap-2">
                <Select
                    startContent={<SortAscendingIcon/>}
                    selectedKeys={[sortBy]}
                    disallowEmptySelection
                    selectionMode="single"
                    onSelectionChange={keys => setSortBy(Array.from(keys)[0] as any)}
                    className="w-full lg:w-64"
                >
                    <SelectItem key="title_asc">Title (A-Z)</SelectItem>
                    <SelectItem key="title_desc">Title (Z-A)</SelectItem>
                    <SelectItem key="release_desc">Release Date (Newest)</SelectItem>
                    <SelectItem key="release_asc">Release Date (Oldest)</SelectItem>
                    <SelectItem key="rating_desc">Rating (Highest)</SelectItem>
                    <SelectItem key="rating_asc">Rating (Lowest)</SelectItem>
                    <SelectItem key="added_desc">Date Added (Newest)</SelectItem>
                    <SelectItem key="added_asc">Date Added (Oldest)</SelectItem>
                    <SelectItem key="updated_desc">Last Updated (Newest)</SelectItem>
                    <SelectItem key="updated_asc">Last Updated (Oldest)</SelectItem>
                </Select>
                <Tooltip content={showFilters ? "Hide Filters" : "Show Filters"}>
                    <Button isIconOnly
                            variant={showFilters ? "solid" : "bordered"}
                            color={showFilters ? "primary" : "default"}
                            onPress={() => setShowFilters(!showFilters)}
                            aria-label="Toggle Filters"
                    >
                        <FunnelSimpleIcon/>
                    </Button>
                </Tooltip>
                <Tooltip content="Clear All Filters">
                    <Button isIconOnly
                            onPress={() => {
                                setSelectedLibraries(new Set());
                                setSelectedDevelopers(new Set());
                                setSelectedGenres(new Set());
                                setSelectedThemes(new Set());
                                setSelectedFeatures(new Set());
                                setSelectedPerspectives(new Set());
                                setSelectedKeywords(new Set());
                                setMinRating(1);
                            }}
                            aria-label="Clear All Filters"
                    >
                        <FunnelSimpleXIcon/>
                    </Button>
                </Tooltip>
            </div>
        </div>
        {showFilters && <div
            className="w-full justify-center px-12"
            style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fit, minmax(250px, 1fr))",
                gap: "0.5rem",
                margin: "0 auto"
            }}
        >
            <Select
                size="sm"
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
                selectionMode="single"
                label="Minimum Rating"
                placeholder="Minimum rating"
                disallowEmptySelection
                selectedKeys={[minRating.toString()]}
                onSelectionChange={keys => setMinRating(parseInt(Array.from(keys)[0] as string, 10))}
                renderValue={(items: SelectedItems<any>) => {
                    return items.map((item) => stars(parseInt(item.key as string)));
                }}
            >
                <SelectItem key="1">{stars(1)}</SelectItem>
                <SelectItem key="2">{stars(2)}</SelectItem>
                <SelectItem key="3">{stars(3)}</SelectItem>
                <SelectItem key="4">{stars(4)}</SelectItem>
                <SelectItem key="5">{stars(5)}</SelectItem>
            </Select>
            <Select
                size="sm"
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
            <Select
                size="sm"
                selectionMode="multiple"
                label="Keywords"
                placeholder="Filter by keyword"
                selectedKeys={selectedKeywords}
                //@ts-ignore
                onSelectionChange={setSelectedKeywords}
            >
                {Array.from(knownKeywords).map((keyword) => (
                    <SelectItem key={keyword}>{keyword}</SelectItem>
                ))}
            </Select>
        </div>
        }
        <div className="mt-4 w-full select-none">
            <CoverGrid games={filteredAndSortedGames}/>
            {filteredAndSortedGames.length === 0 && (
                <div className="text-center mt-8 text-default-500">
                    No games found matching your filters
                </div>
            )}
        </div>
    </div>
}