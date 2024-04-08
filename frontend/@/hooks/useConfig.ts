import {useAtom} from "jotai"
import {atomWithStorage} from "jotai/utils"

import {Theme} from "@/registry/themes"

type Config = {
    theme: {
        name: Theme["name"],
        mode: "light" | "dark" | "system"
    }
}

const configAtom = atomWithStorage<Config>("config", {
    theme: {
        name: "zinc",
        mode: "system"
    }
})

export function useConfig() {
    return useAtom(configAtom)
}
