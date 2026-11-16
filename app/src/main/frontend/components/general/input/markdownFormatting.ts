export type Action = "bold" | "italic" | "heading" | "link" | "bullet" | "number" | "quote" | "code" | "block";

export function formatMarkdown(value: string, selection: { start: number; end: number }, action: Action) {
    let {start, end} = selection;
    let replacement: string;
    let offset = 0;
    let length: number;
    if (["heading", "bullet", "number", "quote", "block"].includes(action)) {
        start = start === 0 ? 0 : value.lastIndexOf("\n", start - 1) + 1;
        const lineEnd = value.indexOf("\n", Math.max(start, end - 1));
        end = lineEnd < 0 ? value.length : lineEnd;
        const text = value.slice(start, end);
        replacement = action === "block" ? `\`\`\`\n${text}\n\`\`\`` : text.split("\n").map((line, i) =>
            `${action === "heading" ? "## " : action === "bullet" ? "- " : action === "number" ? `${i + 1}. ` : "> "}${line}`).join("\n");
        length = replacement.length;
        if (!text) {
            offset = action === "block" ? 4 : replacement.length;
            length = 0;
        }
    } else {
        const text = value.slice(start, end);
        const marker = action === "bold" ? "**" : action === "italic" ? "*" : "`";
        replacement = action === "link" ? (text ? `[${text}](https://example.com)` : "[]()") : `${marker}${text}${marker}`;
        offset = action === "link" ? (text ? text.length + 3 : 1) : marker.length;
        length = action === "link" && text ? "https://example.com".length : text.length;
    }
    return {
        value: value.slice(0, start) + replacement + value.slice(end),
        selection: {start: start + offset, end: start + offset + length}
    };
}
