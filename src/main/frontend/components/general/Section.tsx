import {Divider} from "@nextui-org/react";

export default function Section({title}: { title: string }) {
    return (
        <>
            <h2 className={"text-xl font-bold mt-8"}>{title}</h2>
            <Divider className="mb-4"/>
        </>
    );
}