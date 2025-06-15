type ColorPalette = {
    100: string,
    200: string,
    300: string,
    400: string,
    500: string,
    600: string,
    700: string,
    800: string,
    900: string,
    DEFAULT: string
}

export type Theme = {
    name?: string,
    colors: {
        background?: string,
        foreground?: string,
        primary: ColorPalette,
        secondary?: ColorPalette,
        success?: ColorPalette,
        warning?: ColorPalette,
        danger?: ColorPalette,
        focus?: string,
    }
}