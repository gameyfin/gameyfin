import {hsl} from "Frontend/@/lib/utils";

export default function GameyfinLogo({primary, secondary, className}: {
    primary: string,
    secondary: string,
    className?: string
}) {
    const primaryColor = hsl(primary)
    const secondaryColor = (secondary === null || secondary === undefined) ? primaryColor : hsl(secondary);

    return (
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 365.58 336.34" className={className}>
            <polygon points="190.1 49.13 190.1 69.24 207.98 44.13 190.1 49.13" fill={secondaryColor}/>
            <polygon points="365.58 0 263.22 28.66 205.64 95.97 365.58 51.18 365.58 0" fill={secondaryColor}/>
            <polygon
                points="190.1 283.11 248.6 266.73 248.6 149.74 365.58 116.99 365.58 73.12 190.1 122.25 190.1 283.11"
                fill={secondaryColor}/>
            <polygon
                points="58.49 144.48 155.98 117.18 175.48 89.79 175.48 53.23 0 102.36 0 336.34 58.49 254.15 58.49 144.48"
                fill={primaryColor}/>
            <polygon
                points="116.99 199.59 116.99 245.09 65.81 259.42 0 336.34 175.48 287.2 175.48 170.22 131.61 182.5 116.99 199.59"
                fill={primaryColor}/>
        </svg>
    );
}