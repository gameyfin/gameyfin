import {Pagination} from "@heroui/react";
import {useMemo} from "react";

interface SimplePaginationProps {
    page: number;
    total: number;
    onChange: (page: number) => void;
}

/**
 * v3 Pagination is fully unstyled/composable (no built-in page/total/onChange logic like v2),
 * so this wraps the compound API with the same page/total/onChange contract the rest of the
 * app previously used with v2's `<Pagination>`.
 */
export default function SimplePagination({page, total, onChange}: SimplePaginationProps) {
    const pageNumbers = useMemo(() => {
        const pages: (number | "ellipsis")[] = [];
        if (total <= 7) {
            for (let i = 1; i <= total; i++) pages.push(i);
            return pages;
        }

        pages.push(1);
        if (page > 3) pages.push("ellipsis");

        const start = Math.max(2, page - 1);
        const end = Math.min(total - 1, page + 1);
        for (let i = start; i <= end; i++) pages.push(i);

        if (page < total - 2) pages.push("ellipsis");
        pages.push(total);

        return pages;
    }, [page, total]);

    if (total <= 1) return null;

    return (
        <Pagination>
            <Pagination.Content>
                <Pagination.Item>
                    <Pagination.Previous isDisabled={page <= 1} onPress={() => onChange(page - 1)}>
                        <Pagination.PreviousIcon/>
                    </Pagination.Previous>
                </Pagination.Item>
                {pageNumbers.map((p, index) =>
                    p === "ellipsis" ? (
                        <Pagination.Item key={`ellipsis-${index}`}>
                            <Pagination.Ellipsis/>
                        </Pagination.Item>
                    ) : (
                        <Pagination.Item key={p}>
                            <Pagination.Link isActive={p === page} onPress={() => onChange(p)}>
                                {p}
                            </Pagination.Link>
                        </Pagination.Item>
                    )
                )}
                <Pagination.Item>
                    <Pagination.Next isDisabled={page >= total} onPress={() => onChange(page + 1)}>
                        <Pagination.NextIcon/>
                    </Pagination.Next>
                </Pagination.Item>
            </Pagination.Content>
        </Pagination>
    );
}
