window.applyTheme = () => {
    const theme = window.matchMedia("(prefers-color-scheme: dark)").matches
        ? "dark"
        : "";
    document.documentElement.setAttribute("theme", theme);
};
window
    .matchMedia("(prefers-color-scheme: dark)")
    .addEventListener('change', function () {
        window.applyTheme()
    });
window.applyTheme();