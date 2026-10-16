import {CSSProperties, ImgHTMLAttributes, useEffect, useMemo, useState} from "react";
import {blurhashToDataUrl, imageRetryDelay, MAX_IMAGE_RETRIES, withRetryParam} from "Frontend/util/images";

interface BlurhashImageProps extends Omit<ImgHTMLAttributes<HTMLImageElement>, "src" | "onLoad" | "onError"> {
    src: string;
    blurhash?: string;
    /** Size the blurhash is decoded to, should roughly match the aspect ratio of the image */
    placeholderSize?: { width: number; height: number };
}

/**
 * Image which shows its blurhash while it is loading or not available (yet), e.g. while images are being downloaded
 * again after a backup has been restored. Images which could not be loaded are retried with an increasing delay.
 */
export default function BlurhashImage({
                                          src,
                                          blurhash,
                                          placeholderSize = {width: 32, height: 18},
                                          style,
                                          ...props
                                      }: BlurhashImageProps) {
    const [attempt, setAttempt] = useState(0);
    const [isLoaded, setIsLoaded] = useState(false);
    const [hasError, setHasError] = useState(false);

    const placeholder = useMemo(
        () => blurhash ? blurhashToDataUrl(blurhash, placeholderSize.width, placeholderSize.height) : undefined,
        [blurhash, placeholderSize.width, placeholderSize.height]
    );

    useEffect(() => {
        setAttempt(0);
        setIsLoaded(false);
        setHasError(false);
    }, [src]);

    useEffect(() => {
        if (!hasError || attempt >= MAX_IMAGE_RETRIES) return;
        const timeout = setTimeout(() => {
            setAttempt(attempt + 1);
            setHasError(false);
        }, imageRetryDelay(attempt));
        return () => clearTimeout(timeout);
    }, [hasError, attempt]);

    const imageUrl = withRetryParam(src, attempt);

    const placeholderStyle: CSSProperties | undefined = placeholder && !isLoaded ? {
        backgroundImage: `url(${placeholder})`,
        backgroundSize: "cover",
        backgroundPosition: "center"
    } : undefined;

    return (
        <img
            {...props}
            src={hasError && placeholder ? placeholder : imageUrl}
            style={{...placeholderStyle, ...style}}
            onLoad={() => {
                if (!hasError) setIsLoaded(true);
            }}
            onError={() => {
                if (!hasError) setHasError(true);
            }}
        />
    );
}
