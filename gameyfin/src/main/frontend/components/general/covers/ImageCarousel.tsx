import {Autoplay, Virtual} from 'swiper/modules';
import {Swiper, SwiperSlide} from "swiper/react";
import {Card, Image, Modal, ModalContent, useDisclosure} from "@heroui/react";
import ReactPlayer from 'react-player';

import "swiper/css";
import "swiper/css/navigation";
import "swiper/css/pagination";
import "swiper/css/autoplay";
import {useEffect, useState} from "react";


interface ImageCarouselProps {
    imageUrls?: string[];
    videosUrls?: string[];
}

interface SlideData {
    isActive: boolean;
    isVisible: boolean;
    isPrev: boolean;
    isNext: boolean;
}

export default function ImageCarousel({imageUrls, videosUrls}: ImageCarouselProps) {

    interface CarouselElement {
        type: "image" | "video";
        url: string;
    }

    const SLIDES_PER_VIEW = 3;

    const [elements, setElements] = useState<CarouselElement[]>();
    const [selectedImageUrl, setSelectedImageUrl] = useState<string>();
    const imagePopup = useDisclosure();

    useEffect(() => {
        const images = imageUrls?.map((imageUrl) => ({
            type: "image" as const,
            url: imageUrl
        })) || [];
        const videos = videosUrls?.map((videoUrl) => ({
            type: "video" as const,
            url: videoUrl
        })) || [];

        if ((images.length + videos.length) > SLIDES_PER_VIEW) {
            let elements = [...videos, ...images];
            // Add the last element to the start of the array and the first element to the end of the array to create a loop
            setElements([elements[elements.length - 1], ...elements, elements[0]]);
        } else {
            setElements([...videos, ...images]);
        }
    }, [])

    function showImagePopup(imageUrl: string) {
        setSelectedImageUrl(imageUrl);
        imagePopup.onOpen();
    }

    return (
        <div className="flex flex-col gap-2 bg-transparent">
            <Swiper
                modules={[Virtual, Autoplay]}
                virtual={true}
                slidesPerView={SLIDES_PER_VIEW}
                spaceBetween={0}
                autoplay={{
                    delay: 10000,
                    waitForTransition: false,
                    pauseOnMouseEnter: true
                }}
                className="w-full"
            >
                {elements && elements.map((e, index) => (
                    <SwiperSlide key={index} virtualIndex={index}>
                        {({isNext}: SlideData) => {
                            if (e.type === "image") {
                                return (
                                    <Image
                                        src={e.url}
                                        alt={`Game screenshot slide ${index}`}
                                        className={`w-full h-full object-cover aspect-[16/9] cursor-zoom-in ${!isNext ? "scale-90" : ""}`}
                                        onClick={() => showImagePopup(e.url)}
                                    />
                                )
                            }
                            return (
                                <Card
                                    className={`w-full h-full aspect-[16/9] ${!isNext ? "scale-90" : ""}`}>
                                    <ReactPlayer
                                        url={e.url}
                                        width="100%"
                                        height="100%"
                                    />
                                </Card>
                            )
                        }}
                    </SwiperSlide>
                ))}
            </Swiper>
            <ImagePopup imageUrl={selectedImageUrl} isOpen={imagePopup.isOpen} onOpenChange={imagePopup.onOpenChange}/>
        </div>
    );
}

function ImagePopup({imageUrl, isOpen, onOpenChange}: {
    imageUrl?: string,
    isOpen: boolean,
    onOpenChange: (isOpen: boolean) => void
}) {
    return (imageUrl &&
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} hideCloseButton size="full" backdrop="blur">
            <ModalContent className="bg-transparent">
                {(onClose) => (
                    <div className="flex flex-grow items-center justify-center cursor-zoom-out"
                         onClick={onClose}>
                        <Image
                            src={imageUrl}
                            alt="Game screenshot"
                            className="max-w-[80vw] max-h-[80vh] object-contain"
                        />
                    </div>
                )}
            </ModalContent>
        </Modal>
    )
}