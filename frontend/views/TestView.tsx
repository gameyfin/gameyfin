import {Link} from "react-router-dom";
import {Button} from "@nextui-org/react";
import {toast} from "sonner";
import {SystemEndpoint} from "Frontend/generated/endpoints";

export default function TestView() {
    return (
        <div className="grow justify-center mt-12">
            <div className="flex flex-col items-center gap-6">
                <Link to="/setup">Setup</Link>
                <div className="flex flex-row gap-4">
                    <Button onPress={
                        () => toast("Normal", {
                            description: "Description",
                            action: {
                                label: "OK",
                                onClick: () => {},
                            }
                        })}>Toast (Normal)</Button>
                    <Button onPress={
                        () => toast.success("Success", {
                            description: "Description",
                            action: {
                                label: "OK",
                                onClick: () => {},
                            }
                        })}>Toast (Success)</Button>
                    <Button onPress={
                        () => toast.error("Error", {
                            description: "Description",
                            action: {
                                label: "OK",
                                onClick: () => {},
                            }
                        })}>Toast (Error)</Button>
                </div>
                <Button onPress={() => SystemEndpoint.restart()}>Restart</Button>
            </div>
        </div>
    );
}