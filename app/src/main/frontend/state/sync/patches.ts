/**
 * Describes where the value of a patch field is stored in the summary and full DTO.
 * - `value`: Property name in the patch values DTO
 * - `summary` / `full`: Dot-separated property path in the respective DTO, or undefined if not contained
 */
export interface PatchTarget {
    value: string;
    summary?: string;
    full?: string;
}

export type PatchMapping<F extends string> = Record<F, PatchTarget[]>;

export interface EntityPatch<F extends string> {
    updatedAt: string;
    fields: F[];
    values: object;
}

/**
 * Returns a copy of [target] with the patched fields applied.
 * A listed field without a value has been cleared on the server.
 */
export function applyPatch<T extends object, F extends string>(
    target: T,
    patch: EntityPatch<F>,
    mapping: PatchMapping<F>,
    tier: "summary" | "full"
): T {
    const result: Record<string, unknown> = {...target, updatedAt: patch.updatedAt};
    const values = patch.values as Record<string, unknown>;

    for (const field of patch.fields) {
        for (const entry of mapping[field] ?? []) {
            const path = entry[tier];
            if (path) setPath(result, path.split("."), values[entry.value]);
        }
    }

    return result as T;
}

/** Sets the value at the given path, copying all intermediate objects */
function setPath(target: Record<string, unknown>, path: string[], value: unknown) {
    const [key, ...rest] = path;
    if (rest.length === 0) {
        if (value === undefined || value === null) delete target[key];
        else target[key] = value;
        return;
    }

    const child = {...(target[key] as Record<string, unknown> | undefined ?? {})};
    setPath(child, rest, value);
    target[key] = child;
}

/** Mapping for patches whose value names equal the property names in the DTOs */
export function identityMapping<F extends string>(
    fields: Record<F, string>,
    tiers: { summary?: F[]; full?: F[] } = {}
): PatchMapping<F> {
    const mapping = {} as PatchMapping<F>;
    for (const field of Object.keys(fields) as F[]) {
        const property = fields[field];
        mapping[field] = [{
            value: property,
            summary: !tiers.summary || tiers.summary.includes(field) ? property : undefined,
            full: !tiers.full || tiers.full.includes(field) ? property : undefined
        }];
    }
    return mapping;
}
