import {Divider} from "@heroui/react";

export default function Section({title}: { title: string }) {
    return (
        <>
            <h2 className={"text-xl font-bold mt-8 mb-1"}>{title}</h2>
            <Divider className="mb-4"/>
        </>
    );
}