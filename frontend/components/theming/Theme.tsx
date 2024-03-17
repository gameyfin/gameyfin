export class Theme {
    constructor(
        public readonly name: string,
        public readonly primary: string,
        public readonly secondary?: string,
        public readonly tertiary?: string
    ) {
    }
}