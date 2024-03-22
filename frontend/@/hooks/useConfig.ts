import {useAtom} from "jotai"
import {atomWithStorage} from "jotai/utils"

import {Theme} from "@/registry/themes"

type Config = {
    theme: Theme["name"]
    mode: "light" | "dark",
    radius: number
}

const configAtom = atomWithStorage<Config>("config", {
    theme: "zinc",
    mode: "light",
    radius: 0.5,
})

export function useConfig() {
    return useAtom(configAtom)
}
