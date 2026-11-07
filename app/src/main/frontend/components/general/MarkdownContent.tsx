import Markdown from "react-markdown";
import remarkBreaks from "remark-breaks";
import {Link, Typography} from "@heroui/react";
import type {ReactNode} from "react";

const components = {
    a: ({href, children}: { href?: string; children?: ReactNode }) => (
        <Link href={href} target="_blank" rel="noopener noreferrer"
              className="inline-flex items-center gap-1 text-inherit [font-size:inherit] underline">
            {children}<Link.Icon/>
        </Link>
    )
};

export default function MarkdownContent({children, className = ""}: { children?: string; className?: string }) {
    return <Typography.Prose
        className={`min-w-0 text-sm leading-normal [&_p]:leading-normal [&_li]:leading-normal [&_p]:text-inherit [&_li]:text-inherit [&_p]:[font-size:inherit] [&_li]:[font-size:inherit] [&_p]:m-0 [&_h1]:my-4 [&_h2]:my-4 [&_h3]:my-3 [&_h4]:my-3 [&_h5]:my-2 [&_h6]:my-2 [&_li>ul]:my-1 [&_li>ol]:my-1 [&_img]:max-w-full [&_pre]:overflow-x-auto [&_pre_code]:p-0 *:first:mt-0 *:last:mb-0 ${className}`}>
        <Markdown remarkPlugins={[remarkBreaks]} components={components}>{children}</Markdown>
    </Typography.Prose>;
}
