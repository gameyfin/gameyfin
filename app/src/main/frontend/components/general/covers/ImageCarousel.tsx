import {Autoplay, Navigation, Pagination} from 'swiper/modules';
import {Swiper, SwiperSlide} from "swiper/react";
import {Card, Modal, useOverlayState} from "@heroui/react";
import ReactPlayerModule from 'react-player';

import "swiper/css";
import "swiper/css/navigation";
import "swiper/css/pagination";
import "swiper/css/autoplay";
import {useEffect, useState} from "react";
import {CaretLeftIcon, CaretRightIcon, IconContext, PlayIcon} from "@phosphor-icons/react";
import BlurhashImage from "Frontend/components/general/covers/BlurhashImage";
import {orderMedia} from "Frontend/util/mediaOrder";

// react-player v2 is CJS; depending on the bundler interop the default import may be the module namespace
const ReactPlayer = ((ReactPlayerModule as any).default ?? ReactPlayerModule) as typeof ReactPlayerModule;

export interface CarouselImage {
    url: string;
    /** Shown while the image is loading or not available */
    blurhash?: string;
}

interface ImageCarouselProps {
    images?: CarouselImage[];
    videosUrls?: string[];
    mediaOrder?: string[];
    className?: string;
}

interface SlideData {
    isActive: boolean;
    isVisible: boolean;
    isPrev: boolean;
    isNext: boolean;
}

export default function ImageCarousel({images, videosUrls, mediaOrder, className}: ImageCarouselProps) {

    interface CarouselElement {
        type: "image" | "video";
        url: string;
        blurhash?: string;
    }

    const DEFAULT_SLIDES_PER_VIEW = 3;

    const [elements, setElements] = useState<CarouselElement[]>();
    const [selectedImage, setSelectedImage] = useState<CarouselImage>();
    const imagePopup = useOverlayState();

    useEffect(() => {
        const imageElements = images?.map((image) => ({
            type: "image" as const,
            url: image.url,
            blurhash: image.blurhash
        })) || [];
        const videos = videosUrls?.map((videoUrl) => ({
            type: "video" as const,
            url: videoUrl
        })) || [];

        setElements(orderMedia<CarouselElement>(imageElements, videos, mediaOrder));
    }, [images, videosUrls, mediaOrder])

    function showImagePopup(image: CarouselImage) {
        setSelectedImage(image);
        imagePopup.open();
    }

    return (
        <div className={className}>
            {elements && elements.length > 0 &&
                <div className="w-full flex flex-col gap-2 items-center">
                    <div className="w-full flex flex-row items-center">
                        <IconContext.Provider value={{size: 50}}>
                            <CaretLeftIcon className="swiper-custom-button-prev cursor-pointer fill-accent"/>
                            <Swiper
                                modules={[Pagination, Navigation, Autoplay]}
                                slidesPerView={DEFAULT_SLIDES_PER_VIEW > elements.length ? elements.length : DEFAULT_SLIDES_PER_VIEW}
                                pagination={{
                                    clickable: true,
                                    el: ".swiper-custom-pagination"
                                }}
                                navigation={{
                                    prevEl: ".swiper-custom-button-prev",
                                    nextEl: ".swiper-custom-button-next"
                                }}
                                centeredSlides={true}
                                loop={true}
                                spaceBetween={0}
                                autoplay={{
                                    delay: 10000,
                                    disableOnInteraction: true
                                }}
                                className="w-full"
                            >
                                {elements && elements.map((e, index) => (
                                    <SwiperSlide key={index} virtualIndex={index}>
                                        {({isActive}: SlideData) => {
                                            if (e.type === "image") {
                                                return (
                                                    <BlurhashImage
                                                        src={e.url}
                                                        blurhash={e.blurhash}
                                                        alt={`Game screenshot slide ${index}`}
                                                        className={`w-full h-full object-cover aspect-video rounded-xl cursor-zoom-in transition-transform duration-300 ease-in-out ${!isActive ? "scale-90" : ""}`}
                                                        onClick={() => showImagePopup(e)}
                                                        loading="lazy"
                                                    />
                                                )
                                            }
                                            return (
                                                <Card
                                                    className={`w-full h-full aspect-video p-0 rounded-xl overflow-hidden transition-transform duration-300 ease-in-out ${!isActive ? "scale-90" : ""}`}>
                                                    <ReactPlayer
                                                        src={e.url}
                                                        width="100%"
                                                        height="100%"
                                                        light={true}
                                                        controls={true}
                                                        playing={isActive}
                                                        playIcon={<PlayIcon weight="fill"/>}
                                                    />
                                                </Card>
                                            )
                                        }}
                                    </SwiperSlide>
                                ))}
                                <ImagePopup image={selectedImage}
                                            isOpen={imagePopup.isOpen}
                                            onOpenChange={imagePopup.setOpen}
                                            onClose={imagePopup.close}/>
                            </Swiper>
                            <CaretRightIcon className="swiper-custom-button-next cursor-pointer fill-accent"/>
                        </IconContext.Provider>
                    </div>
                    <div>
                        {/* Wrap the pagination in a div because it gets replaced at runtime be SwiperJS and loses all styling */}
                        <div className="swiper-custom-pagination"/>
                    </div>
                </div>
            }
        </div>
    );
}

function ImagePopup({image, isOpen, onOpenChange, onClose}: {
    image?: CarouselImage,
    isOpen: boolean,
    onOpenChange: (isOpen: boolean) => void,
    onClose: () => void
}) {
    return (image &&
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange} variant="blur">
                <Modal.Container size="full">
                    <Modal.Dialog className="bg-transparent shadow-none max-w-none">
                        {() => (
                            <div className="flex grow items-center justify-center cursor-zoom-out"
                                 onClick={onClose}>
                                <BlurhashImage
                                    src={image.url}
                                    blurhash={image.blurhash}
                                    alt="Game screenshot"
                                    className="max-w-[80vw] max-h-[80vh] object-contain rounded-xl"
                                />
                            </div>
                        )}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    )
}
