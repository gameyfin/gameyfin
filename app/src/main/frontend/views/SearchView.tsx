import {Button, Input, ListBox, Select, Tooltip} from "@heroui/react";
import {
    FunnelSimpleIcon,
    FunnelSimpleXIcon,
    MagnifyingGlassIcon,
    SortAscendingIcon,
    StarIcon,
} from "@phosphor-icons/react";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import {libraryState} from "Frontend/state/LibraryState";
import {useSearchParams} from "react-router";
import React, {useEffect, useMemo, useState} from "react";
import {Fzf} from "fzf";
import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import CoverGrid from "Frontend/components/general/covers/CoverGrid";
import {compoundRating} from "Frontend/util/utils";

export default function SearchView() {
    const games = useSnapshot(gameState).sortedAlphabetically;
    const knownDevelopers = useSnapshot(gameState).knownDevelopers;
    const knownGenres = useSnapshot(gameState).knownGenres;
    const knownThemes = useSnapshot(gameState).knownThemes;
    const knownFeatures = useSnapshot(gameState).knownFeatures;
    const knownPerspectives = useSnapshot(gameState).knownPerspectives;
    const knownKeywords = useSnapshot(gameState).knownKeywords;
    const libraries = useSnapshot(libraryState).libraries;

    const [searchParams, setSearchParams] = useSearchParams();
    const [initialLoadComplete, setInitialLoadComplete] = useState(false);

    const [showFilters, setShowFilters] = useState(false);
    const [sortBy, setSortBy] = useState("title_asc");

    const [searchTerm, setSearchTerm] = useState<string>("");
    const [selectedLibraries, setSelectedLibraries] = useState<Set<string>>(new Set());
    const [selectedDevelopers, setSelectedDevelopers] = useState<Set<string>>(new Set());
    const [selectedGenres, setSelectedGenres] = useState<Set<string>>(new Set());
    const [selectedThemes, setSelectedThemes] = useState<Set<string>>(new Set());
    const [selectedFeatures, setSelectedFeatures] = useState<Set<string>>(new Set());
    const [selectedPerspectives, setSelectedPerspectives] = useState<Set<string>>(new Set());
    const [selectedKeywords, setSelectedKeywords] = useState<Set<string>>(new Set());
    const [minRating, setMinRating] = useState<number>(1);

    useEffect(() => {
        window.scrollTo(0, 0);

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

    useEffect(() => {
        if (!initialLoadComplete) return;

        const newParams = new URLSearchParams();

        if (searchTerm && searchTerm.trim() !== "") {
            newParams.set("term", searchTerm);
        }

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
        if (minRating > 1) {
            newParams.set("minRating", minRating.toString());
        }
        if (sortBy && sortBy !== "title_asc") {
            newParams.set("sort", sortBy);
        }
        if (showFilters) {
            newParams.set("filters", "1");
        }

        setSearchParams(newParams, {replace: true});
    }, [searchTerm, selectedLibraries, selectedDevelopers, selectedGenres,
        selectedThemes, selectedFeatures, selectedPerspectives, selectedKeywords, sortBy, minRating, showFilters]);

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
                cmp *= -1;
            }
            return cmp;
        });
    }

    const filteredAndSortedGames = useMemo(() => sortGames(filterGames()), [
        games, searchTerm,
        selectedLibraries, selectedDevelopers,
        selectedGenres, selectedThemes,
        selectedFeatures, selectedPerspectives, selectedKeywords, sortBy, minRating,
    ]);

    function filterGames(): GameDto[] {
        let filtered = games;

        if (searchTerm !== "") {
            const fzf = new Fzf(filtered, {
                selector: (game: GameDto) => game.title
            });
            filtered = fzf.find(searchTerm).map(result => result.item);
        }

        if (selectedLibraries.size > 0) {
            filtered = filtered.filter(game => selectedLibraries.has(game.libraryId.toString()));
        }

        if (selectedDevelopers.size > 0) {
            filtered = filtered.filter(game =>
                game.developers?.some(developer => selectedDevelopers.has(developer))
            );
        }

        if (selectedGenres.size > 0) {
            filtered = filtered.filter(game =>
                game.genres?.some(genre => selectedGenres.has(genre))
            );
        }

        if (selectedThemes.size > 0) {
            filtered = filtered.filter(game =>
                game.themes?.some(theme => selectedThemes.has(theme))
            );
        }

        if (selectedFeatures.size > 0) {
            filtered = filtered.filter(game =>
                game.features?.some(feature => selectedFeatures.has(feature))
            );
        }

        if (selectedPerspectives.size > 0) {
            filtered = filtered.filter(game =>
                game.perspectives?.some(perspective => selectedPerspectives.has(perspective))
            );
        }

        if (selectedKeywords.size > 0) {
            filtered = filtered.filter(game =>
                game.keywords?.some(keyword => selectedKeywords.has(keyword))
            );
        }

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

    function ratingLabel(value: number) {
        return "★".repeat(value) + "☆".repeat(5 - value);
    }

    function renderItems(options: string[]) {
        return options.map((option) => (
            <ListBox.Item key={option} id={option} textValue={option}>
                {option}
                <ListBox.ItemIndicator/>
            </ListBox.Item>
        ));
    }

    function renderSelect(
        label: string,
        value: string | string[],
        onChange: (value: any) => void,
        children: React.ReactNode,
        selectionMode: "single" | "multiple" = "multiple",
        placeholder?: string,
        className?: string,
        disallowEmptySelection?: boolean,
        triggerPrefix?: React.ReactNode,
    ) {
        return (
            <Select
                value={value}
                onChange={onChange}
                selectionMode={selectionMode}
                placeholder={placeholder}
                className={className}
            >
                <span className="text-sm text-muted">{label}</span>
                <Select.Trigger className="flex items-center gap-2">
                    {triggerPrefix}
                    <Select.Value/>
                    <Select.Indicator/>
                </Select.Trigger>
                <Select.Popover>
                    <ListBox>
                        {children}
                    </ListBox>
                </Select.Popover>
            </Select>
        );
    }

    return <div className="flex flex-col gap-4 items-center w-full">
        <div className="flex w-full justify-between px-12 gap-4 flex-col lg:flex-row">
            <div className="flex h-full w-full lg:w-96 shrink-0 items-center gap-2 rounded-lg border border-border bg-default/20 px-3 py-2 text-muted dark:bg-default/20">
                <MagnifyingGlassIcon/>
                <Input
                    className="grow"
                    placeholder="Type to search..."
                    type="search"
                    value={searchTerm}
                    onChange={(event) => setSearchTerm(event.target.value)}
                />
                {searchTerm && (
                    <button className="text-muted" onClick={() => setSearchTerm("")} type="button">
                        ×
                    </button>
                )}
            </div>
            <div className="flex flex-row gap-2">
                {renderSelect(
                    "Sort",
                    sortBy,
                    (value) => setSortBy(value as string),
                    <>
                        <ListBox.Item id="title_asc" key="title_asc" textValue="Title (A-Z)">Title (A-Z)<ListBox.ItemIndicator/></ListBox.Item>
                        <ListBox.Item id="title_desc" key="title_desc" textValue="Title (Z-A)">Title (Z-A)<ListBox.ItemIndicator/></ListBox.Item>
                        <ListBox.Item id="release_desc" key="release_desc" textValue="Release Date (Newest)">Release Date (Newest)<ListBox.ItemIndicator/></ListBox.Item>
                        <ListBox.Item id="release_asc" key="release_asc" textValue="Release Date (Oldest)">Release Date (Oldest)<ListBox.ItemIndicator/></ListBox.Item>
                        <ListBox.Item id="rating_desc" key="rating_desc" textValue="Rating (Highest)">Rating (Highest)<ListBox.ItemIndicator/></ListBox.Item>
                        <ListBox.Item id="rating_asc" key="rating_asc" textValue="Rating (Lowest)">Rating (Lowest)<ListBox.ItemIndicator/></ListBox.Item>
                        <ListBox.Item id="added_desc" key="added_desc" textValue="Date Added (Newest)">Date Added (Newest)<ListBox.ItemIndicator/></ListBox.Item>
                        <ListBox.Item id="added_asc" key="added_asc" textValue="Date Added (Oldest)">Date Added (Oldest)<ListBox.ItemIndicator/></ListBox.Item>
                        <ListBox.Item id="updated_desc" key="updated_desc" textValue="Last Updated (Newest)">Last Updated (Newest)<ListBox.ItemIndicator/></ListBox.Item>
                        <ListBox.Item id="updated_asc" key="updated_asc" textValue="Last Updated (Oldest)">Last Updated (Oldest)<ListBox.ItemIndicator/></ListBox.Item>
                    </>,
                    "single",
                    undefined,
                    "w-full lg:w-64",
                    true,
                    <SortAscendingIcon/>,
                )}
                <Tooltip>
                    <Tooltip.Trigger>
                        <Button isIconOnly
                                variant={showFilters ? "primary" : "outline"}
                                onPress={() => setShowFilters(!showFilters)}
                                aria-label="Toggle Filters"
                        >
                            <FunnelSimpleIcon/>
                        </Button>
                    </Tooltip.Trigger>
                    <Tooltip.Content>{showFilters ? "Hide Filters" : "Show Filters"}</Tooltip.Content>
                </Tooltip>
                <Tooltip>
                    <Tooltip.Trigger>
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
                    </Tooltip.Trigger>
                    <Tooltip.Content>Clear All Filters</Tooltip.Content>
                </Tooltip>
            </div>
        </div>
        {showFilters && <div
            className="w-full justify-center px-12"
            style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fit, minmax(250px, 1fr))",
                gap: "0.5rem",
                margin: "0 auto",
            }}
        >
            {renderSelect(
                "Libraries",
                Array.from(selectedLibraries),
                (value) => setSelectedLibraries(new Set((value as string[]) ?? [])),
                <>{libraries.map((library) => (
                    <ListBox.Item key={library.id} id={library.id.toString()} textValue={library.name}>
                        {library.name}
                        <ListBox.ItemIndicator/>
                    </ListBox.Item>
                ))}</>,
                "multiple",
                "Filter by library",
            )}
            {renderSelect(
                "Minimum Rating",
                minRating.toString(),
                (value) => setMinRating(parseInt(value as string, 10)),
                <>
                    {[1, 2, 3, 4, 5].map((value) => (
                        <ListBox.Item key={value} id={value.toString()} textValue={ratingLabel(value)}>
                            {stars(value)}
                            <ListBox.ItemIndicator/>
                        </ListBox.Item>
                    ))}
                </>,
                "single",
                "Minimum rating",
                undefined,
                true,
            )}
            {renderSelect(
                "Developers",
                Array.from(selectedDevelopers),
                (value) => setSelectedDevelopers(new Set((value as string[]) ?? [])),
                <>{renderItems(Array.from(knownDevelopers))}</>,
                "multiple",
                "Filter by developer",
            )}
            {renderSelect(
                "Genres",
                Array.from(selectedGenres),
                (value) => setSelectedGenres(new Set((value as string[]) ?? [])),
                <>{renderItems(Array.from(knownGenres))}</>,
                "multiple",
                "Filter by genre",
            )}
            {renderSelect(
                "Themes",
                Array.from(selectedThemes),
                (value) => setSelectedThemes(new Set((value as string[]) ?? [])),
                <>{renderItems(Array.from(knownThemes))}</>,
                "multiple",
                "Filter by theme",
            )}
            {renderSelect(
                "Features",
                Array.from(selectedFeatures),
                (value) => setSelectedFeatures(new Set((value as string[]) ?? [])),
                <>{renderItems(Array.from(knownFeatures))}</>,
                "multiple",
                "Filter by feature",
            )}
            {renderSelect(
                "Perspectives",
                Array.from(selectedPerspectives),
                (value) => setSelectedPerspectives(new Set((value as string[]) ?? [])),
                <>{renderItems(Array.from(knownPerspectives))}</>,
                "multiple",
                "Filter by perspective",
            )}
            {renderSelect(
                "Keywords",
                Array.from(selectedKeywords),
                (value) => setSelectedKeywords(new Set((value as string[]) ?? [])),
                <>{renderItems(Array.from(knownKeywords))}</>,
                "multiple",
                "Filter by keyword",
            )}
        </div>
        }
        <div className="mt-4 w-full select-none">
            <CoverGrid games={filteredAndSortedGames}/>
            {filteredAndSortedGames.length === 0 && (
                <div className="text-center mt-8 text-muted">
                    No games found matching your filters
                </div>
            )}
        </div>
    </div>;
}
