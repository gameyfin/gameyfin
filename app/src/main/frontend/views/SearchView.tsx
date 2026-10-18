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
import {Form, Formik, useFormikContext} from "formik";
import {Fzf} from "fzf";
import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import CoverGrid from "Frontend/components/general/covers/CoverGrid";
import AutocompleteInput from "Frontend/components/general/input/AutocompleteInput";
import {compoundRating} from "Frontend/util/utils";

type SearchFormValues = {
    searchTerm: string;
    sortBy: string;
    libraries: string[];
    developers: string[];
    genres: string[];
    themes: string[];
    features: string[];
    perspectives: string[];
    keywords: string[];
    minRating: number;
};

const SORT_OPTIONS = [
    {id: "title_asc", label: "Title (A-Z)"},
    {id: "title_desc", label: "Title (Z-A)"},
    {id: "release_desc", label: "Release Date (Newest)"},
    {id: "release_asc", label: "Release Date (Oldest)"},
    {id: "rating_desc", label: "Rating (Highest)"},
    {id: "rating_asc", label: "Rating (Lowest)"},
    {id: "added_desc", label: "Date Added (Newest)"},
    {id: "added_asc", label: "Date Added (Oldest)"},
    {id: "updated_desc", label: "Last Updated (Newest)"},
    {id: "updated_asc", label: "Last Updated (Oldest)"},
];

export default function SearchView() {
    const [searchParams] = useSearchParams();

    const [initialValues] = useState<SearchFormValues>(() => {
        const minRatingParam = parseInt(searchParams.get("minRating") || "1", 10);
        return {
            searchTerm: searchParams.get("term") || "",
            sortBy: searchParams.get("sort") || "title_asc",
            libraries: searchParams.getAll("lib"),
            developers: searchParams.getAll("dev"),
            genres: searchParams.getAll("genre"),
            themes: searchParams.getAll("theme"),
            features: searchParams.getAll("feature"),
            perspectives: searchParams.getAll("perspective"),
            keywords: searchParams.getAll("keyword"),
            minRating: isNaN(minRatingParam) ? 1 : minRatingParam,
        };
    });

    return (
        <Formik initialValues={initialValues} onSubmit={() => {
        }}>
            <SearchViewContent/>
        </Formik>
    );
}

function SearchViewContent() {
    const games = useSnapshot(gameState).sortedAlphabetically;
    const knownDevelopers = useSnapshot(gameState).knownDevelopers;
    const knownGenres = useSnapshot(gameState).knownGenres;
    const knownThemes = useSnapshot(gameState).knownThemes;
    const knownFeatures = useSnapshot(gameState).knownFeatures;
    const knownPerspectives = useSnapshot(gameState).knownPerspectives;
    const knownKeywords = useSnapshot(gameState).knownKeywords;
    const libraries = useSnapshot(libraryState).libraries;

    const [searchParams, setSearchParams] = useSearchParams();
    const {values, setFieldValue, setValues} = useFormikContext<SearchFormValues>();
    const {
        searchTerm, sortBy, minRating,
        libraries: selectedLibraries,
        developers: selectedDevelopers,
        genres: selectedGenres,
        themes: selectedThemes,
        features: selectedFeatures,
        perspectives: selectedPerspectives,
        keywords: selectedKeywords,
    } = values;

    const [showFilters, setShowFilters] = useState(() => searchParams.get("filters") === "1");

    useEffect(() => {
        window.scrollTo(0, 0);
    }, []);

    useEffect(() => {
        const newParams = new URLSearchParams();

        if (searchTerm && searchTerm.trim() !== "") {
            newParams.set("term", searchTerm);
        }
        selectedLibraries.forEach(lib => newParams.append("lib", lib));
        selectedDevelopers.forEach(dev => newParams.append("dev", dev));
        selectedGenres.forEach(genre => newParams.append("genre", genre));
        selectedThemes.forEach(theme => newParams.append("theme", theme));
        selectedFeatures.forEach(feature => newParams.append("feature", feature));
        selectedPerspectives.forEach(perspective => newParams.append("perspective", perspective));
        selectedKeywords.forEach(keyword => newParams.append("keyword", keyword));
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
    }, [values, showFilters]);

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

        if (selectedLibraries.length > 0) {
            filtered = filtered.filter(game => selectedLibraries.includes(game.libraryId.toString()));
        }

        if (selectedDevelopers.length > 0) {
            filtered = filtered.filter(game =>
                game.developers?.some(developer => selectedDevelopers.includes(developer))
            );
        }

        if (selectedGenres.length > 0) {
            filtered = filtered.filter(game =>
                game.genres?.some(genre => selectedGenres.includes(genre))
            );
        }

        if (selectedThemes.length > 0) {
            filtered = filtered.filter(game =>
                game.themes?.some(theme => selectedThemes.includes(theme))
            );
        }

        if (selectedFeatures.length > 0) {
            filtered = filtered.filter(game =>
                game.features?.some(feature => selectedFeatures.includes(feature))
            );
        }

        if (selectedPerspectives.length > 0) {
            filtered = filtered.filter(game =>
                game.perspectives?.some(perspective => selectedPerspectives.includes(perspective))
            );
        }

        if (selectedKeywords.length > 0) {
            filtered = filtered.filter(game =>
                game.keywords?.some(keyword => selectedKeywords.includes(keyword))
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

    function toOptions(options: Iterable<string>): string[] {
        return Array.from(options);
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

    return <Form className="flex flex-col gap-4 items-center w-full">
        <div className="flex w-full justify-between px-4 gap-4 flex-col lg:flex-row">
            <div
                className="flex h-full w-full lg:w-96 shrink-0 items-center gap-2 rounded-lg px-3 py-2 text-muted">
                <MagnifyingGlassIcon/>
                <Input
                    className="grow"
                    placeholder="Type to search..."
                    type="search"
                    value={searchTerm}
                    onChange={(event) => setFieldValue("searchTerm", event.target.value)}
                />
                {searchTerm && (
                    <button className="text-muted" onClick={() => setFieldValue("searchTerm", "")} type="button">
                        ×
                    </button>
                )}
            </div>
            <div className="flex flex-row items-end gap-2">
                <AutocompleteInput
                    name="sortBy"
                    label="Sort"
                    options={SORT_OPTIONS}
                    selectionMode="single"
                    className="flex flex-col gap-1 w-full lg:w-64"
                    triggerPrefix={<SortAscendingIcon/>}
                    showError={false}
                />
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
                                onPress={() => setValues({
                                    ...values,
                                    libraries: [],
                                    developers: [],
                                    genres: [],
                                    themes: [],
                                    features: [],
                                    perspectives: [],
                                    keywords: [],
                                    minRating: 1,
                                })}
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
            <AutocompleteInput name="libraries" label="Libraries" placeholder="Filter by library"
                               options={libraries.map((library) => ({id: library.id.toString(), label: library.name}))}
                               className="flex flex-col gap-1" showError={false}/>
            {renderSelect(
                "Minimum Rating",
                minRating.toString(),
                (value) => setFieldValue("minRating", parseInt(value as string, 10)),
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
            <AutocompleteInput name="developers" label="Developers" placeholder="Filter by developer"
                               options={toOptions(knownDevelopers)} className="flex flex-col gap-1" showError={false}/>
            <AutocompleteInput name="genres" label="Genres" placeholder="Filter by genre"
                               options={toOptions(knownGenres)} className="flex flex-col gap-1" showError={false}/>
            <AutocompleteInput name="themes" label="Themes" placeholder="Filter by theme"
                               options={toOptions(knownThemes)} className="flex flex-col gap-1" showError={false}/>
            <AutocompleteInput name="features" label="Features" placeholder="Filter by feature"
                               options={toOptions(knownFeatures)} className="flex flex-col gap-1" showError={false}/>
            <AutocompleteInput name="perspectives" label="Perspectives" placeholder="Filter by perspective"
                               options={toOptions(knownPerspectives)} className="flex flex-col gap-1" showError={false}/>
            <AutocompleteInput name="keywords" label="Keywords" placeholder="Filter by keyword"
                               options={toOptions(knownKeywords)} className="flex flex-col gap-1" showError={false}/>
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
    </Form>;
}
