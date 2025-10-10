#!/usr/bin/env node
/*
 * Migration script: Append 'Icon' suffix to Phosphor icon component usages.
 *
 * What it does:
 *  - Scans all .tsx files under src/main/frontend for import declarations from '@phosphor-icons/react'.
 *  - For each named import that does NOT already end with 'Icon', rewrites the import (imported + local alias) to add 'Icon'.
 *  - Rewrites all usages (JSX tags and identifiers) of the old local name to the new suffixed name.
 *  - Skips any icon name that already exists in the same import specifier list with the suffixed form to avoid collisions.
 *  - Skips non-icon utility exports like Icon and IconContext.
 *  - Provides a --dry-run mode to preview changes without writing.
 *
 * Usage:
 *   node scripts/migrate-phosphor-icons.js            # perform migration (writes files)
 *   node scripts/migrate-phosphor-icons.js --dry-run  # show planned changes only
 */
import fs from 'fs';
import path from 'path';
import {globSync} from 'glob';

const ROOT = process.cwd();
const DRY_RUN = process.argv.includes('--dry-run');
const GLOB_PATTERN = 'src/main/frontend/**/*.tsx';
const SKIP_RENAMES = new Set(['Icon', 'IconContext']);

function collectFiles() {
    return globSync(GLOB_PATTERN, {cwd: ROOT, nodir: true, absolute: true});
}

function findImportSection(code) {
    const importRegex = /import\s+([^;]*?)from\s+['"]@phosphor-icons\/react['"];?/g; // matches multi-line until semicolon
    const matches = [];
    let m;
    while ((m = importRegex.exec(code)) !== null) {
        matches.push({full: m[0], spec: m[1], index: m.index});
    }
    return matches;
}

function parseNamedImports(specPart) {
    const namedRegex = /\{([^}]*)}/; // first occurrence
    const m = namedRegex.exec(specPart);
    if (!m) return [];
    return m[1]
        .split(',')
        .map(s => s.trim())
        .filter(Boolean)
        .map(seg => {
            const parts = seg.split(/\s+as\s+/i).map(p => p.trim());
            if (parts.length === 2) {
                return {imported: parts[0], local: parts[1], raw: seg};
            }
            return {imported: parts[0], local: parts[0], raw: seg};
        });
}

function buildNamedImportString(imports) {
    return '{ ' + imports.map(i => (i.imported === i.local ? i.local : `${i.imported} as ${i.local}`)).join(', ') + ' }';
}

function applyRenames(code, importMatch, renames) {
    if (renames.length === 0) return code;
    const {full, spec} = importMatch;
    const namedImports = parseNamedImports(spec);
    if (namedImports.length === 0) return code;
    const renameMap = Object.fromEntries(renames.map(r => [r.oldLocal, r.newLocal]));
    const importedRenameMap = Object.fromEntries(renames.map(r => [r.oldImported, r.newImported]));

    for (const ni of namedImports) {
        if (renameMap[ni.local]) {
            ni.local = renameMap[ni.local];
        }
        if (importedRenameMap[ni.imported]) {
            ni.imported = importedRenameMap[ni.imported];
        }
    }

    const newNamed = buildNamedImportString(namedImports);
    const newImport = full.replace(/\{[^}]*}/, newNamed);
    let newCode = code.replace(full, newImport);

    for (const {oldLocal, newLocal} of renames) {
        const jsxTagRegex = new RegExp(`(<\/?)(?:${oldLocal})(?=(?:[\s>/]))`, 'g');
        newCode = newCode.replace(jsxTagRegex, `$1${newLocal}`);
        const idRegex = new RegExp(`\\b${oldLocal}\\b`, 'g');
        newCode = newCode.replace(idRegex, newLocal);
    }
    return newCode;
}

function processFile(file) {
    const original = fs.readFileSync(file, 'utf8');
    const imports = findImportSection(original);
    if (imports.length === 0) return null;
    let updated = original;
    let fileRenames = [];

    for (const imp of imports) {
        const named = parseNamedImports(imp.spec);
        if (named.length === 0) continue;
        const existingNames = new Set(named.map(n => n.local));
        const renames = [];
        for (const n of named) {
            if (SKIP_RENAMES.has(n.local) || SKIP_RENAMES.has(n.imported)) continue;
            if (!n.local.endsWith('Icon')) {
                const newLocal = n.local + 'Icon';
                if (!existingNames.has(newLocal)) {
                    const newImported = n.imported.endsWith('Icon') ? n.imported : n.imported + 'Icon';
                    renames.push({
                        oldLocal: n.local,
                        newLocal,
                        oldImported: n.imported,
                        newImported,
                        summary: `${n.imported}${n.imported === n.local ? '' : ' as ' + n.local} -> ${newImported}${newImported === newLocal ? '' : ' as ' + newLocal}`
                    });
                }
            }
        }
        if (renames.length === 0) continue;
        fileRenames.push(...renames.map(r => r.summary));
        updated = applyRenames(updated, imp, renames);
    }

    if (fileRenames.length === 0) return null;
    if (!DRY_RUN) {
        fs.writeFileSync(file, updated, 'utf8');
    }
    return {file, changes: fileRenames};
}

function main() {
    const files = collectFiles();
    const results = [];
    for (const f of files) {
        const res = processFile(f);
        if (res) results.push(res);
    }
    if (results.length === 0) {
        console.log('No icon imports requiring migration were found.');
        return;
    }
    console.log(`${DRY_RUN ? 'Planned' : 'Applied'} migrations for ${results.length} file(s):`);
    for (const r of results) {
        console.log('- ' + path.relative(ROOT, r.file));
        r.changes.forEach(ch => console.log('    ' + ch));
    }
    if (DRY_RUN) {
        console.log('\nRe-run without --dry-run to apply these changes.');
    }
}

main();