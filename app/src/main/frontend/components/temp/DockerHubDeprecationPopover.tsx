import {Button, Link, Popover, PopoverContent, PopoverTrigger} from "@heroui/react";
import { WarningIcon } from "@phosphor-icons/react";

// TODO: Remove this component before the release of version 2.2.0
export default function DockerHubDeprecationPopover() {
    return (
        <Popover placement="bottom-end" showArrow={true} color="warning">
            <PopoverTrigger>
                <Button isIconOnly color="warning" variant="flat">
                    <WarningIcon/>
                </Button>
            </PopoverTrigger>
            <PopoverContent>
                <div className="m-4 text-sm leading-relaxed">
                    <h3 className="mb-2 font-bold">Image deprecation notice</h3>
                    <p>
                        Starting with version
                        <code className="font-semibold"> 2.2.0 </code>
                        the image{' '}
                        <Link href="https://hub.docker.com/r/grimsi/gameyfin"
                              isExternal
                              underline="always"
                              size="sm"
                              className="text-warning-contrast">
                            grimsi/gameyfin
                        </Link>
                        {' '}will no longer be published to Docker Hub.
                    </p>
                    <p>
                        Please switch to{' '}
                        <Link href="https://github.com/gameyfin/gameyfin/pkgs/container/gameyfin"
                              isExternal
                              underline="always"
                              size="sm"
                              className="text-warning-contrast">
                            ghcr.io/gameyfin/gameyfin
                        </Link>
                        {' '}if you are currently using the Docker Hub image.
                    </p>
                </div>
            </PopoverContent>
        </Popover>
    );
}