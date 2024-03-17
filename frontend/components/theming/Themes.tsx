import {Theme} from "Frontend/components/theming/Theme";

export class Themes {
    public static LIGHT_DEFAULT = new Theme(
        "Light default",
        "#000000"
    )

    public static DARK_DEFAULT = new Theme(
        "Dark default",
        "#ffffff"
    )

    public static all = [
        Themes.LIGHT_DEFAULT,
        Themes.DARK_DEFAULT
    ];
}