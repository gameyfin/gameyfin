import {Autoplay, Navigation, Pagination} from 'swiper/modules';
import {Swiper, SwiperSlide} from "swiper/react";
import {Card, Modal, useOverlayState} from "@heroui/react";
import ReactPlayer from 'react-player';

import "swiper/css";
import "swiper/css/navigation";
import "swiper/css/pagination";
import "swiper/css/autoplay";
import {useEffect, useState} from "react";
import { CaretLeftIcon, CaretRightIcon, IconContext, PlayIcon } from "@phosphor-icons/react";


interface ImageCarouselProps {
    imageUrls?: string[];
    videosUrls?: string[];
    className?: string;
}

interface SlideData {
    isActive: boolean;
    isVisible: boolean;
    isPrev: boolean;
    isNext: boolean;
}

export default function ImageCarousel({imageUrls, videosUrls, className}: ImageCarouselProps) {

    interface CarouselElement {
        type: "image" | "video";
        url: string;
    }

    const DEFAULT_SLIDES_PER_VIEW = 3;

    const [elements, setElements] = useState<CarouselElement[]>();
    const [selectedImageUrl, setSelectedImageUrl] = useState<string>();
    const imagePopup = useOverlayState();

    useEffect(() => {
        const images = imageUrls?.map((imageUrl) => ({
            type: "image" as const,
            url: imageUrl
        })) || [];
        const videos = videosUrls?.map((videoUrl) => ({
            type: "video" as const,
            url: videoUrl
        })) || [];

        setElements([...images, ...videos]);
    }, [imageUrls, videosUrls])

    function showImagePopup(imageUrl: string) {
        setSelectedImageUrl(imageUrl);
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
                                                    <img
                                                        src={e.url}
                                                        alt={`Game screenshot slide ${index}`}
                                                        className={`w-full h-full object-cover aspect-video cursor-zoom-in ${!isActive ? "scale-90" : ""}`}
                                                        onClick={() => showImagePopup(e.url)}
                                                        loading="lazy"
                                                    />
                                                )
                                            }
                                            return (
                                                <Card
                                                    className={`w-full h-full aspect-video ${!isActive ? "scale-90" : ""}`}>
                                                    <ReactPlayer
                                                        url={e.url}
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
                                <ImagePopup imageUrl={selectedImageUrl}
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

function ImagePopup({imageUrl, isOpen, onOpenChange, onClose}: {
    imageUrl?: string,
    isOpen: boolean,
    onOpenChange: (isOpen: boolean) => void,
    onClose: () => void
}) {
    return (imageUrl &&
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange} variant="blur">
                <Modal.Container size="full">
                    <Modal.Dialog className="bg-transparent shadow-none max-w-none">
                        {() => (
                            <div className="flex grow items-center justify-center cursor-zoom-out"
                                 onClick={onClose}>
                                <img
                                    src={imageUrl}
                                    alt="Game screenshot"
                                    className="max-w-[80vw] max-h-[80vh] object-contain"
                                />
                            </div>
                        )}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    )
}