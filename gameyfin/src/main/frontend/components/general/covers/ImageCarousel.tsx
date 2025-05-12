import {Autoplay, Virtual} from 'swiper/modules';
import {Swiper, SwiperSlide} from "swiper/react";
import {Image} from "@heroui/react";

import "swiper/css";
import "swiper/css/navigation";
import "swiper/css/pagination";
import "swiper/css/autoplay";

interface ImageCarouselProps {
    imageIds: number[];
}

interface SlideData {
    isActive: boolean;
    isVisible: boolean;
    isPrev: boolean;
    isNext: boolean;
}

export default function ImageCarousel({imageIds}: ImageCarouselProps) {

    return (
        <div className="flex flex-col gap-2 bg-transparent">
            <Swiper
                modules={[Virtual, Autoplay]}
                virtual={true}
                slidesPerView={3}
                spaceBetween={0}
                autoplay={{
                    delay: 5000,
                    waitForTransition: false,
                    pauseOnMouseEnter: true
                }}
                className="w-full"
            >
                <SwiperSlide key={imageIds[imageIds.length]} virtualIndex={0}>
                    <Image
                        src={`/images/screenshot/${imageIds[imageIds.length - 1]}`}
                        alt={`Game screenshot slide ${imageIds.length + 2}`}
                        className="w-full h-full object-cover aspect-[16/9] scale-90"
                    />
                </SwiperSlide>
                {imageIds.map((imageId, index) => (
                    <SwiperSlide key={imageId} virtualIndex={index + 1}>
                        {({isNext}: SlideData) => (
                            <Image
                                src={`/images/screenshot/${imageId}`}
                                alt={`Game screenshot slide ${index + 1}`}
                                className={`w-full h-full object-cover aspect-[16/9] ${!isNext ? "scale-90" : ""}`}
                            />
                        )}
                    </SwiperSlide>
                ))}
                <SwiperSlide key={imageIds[0]} virtualIndex={imageIds.length + 2}>
                    <Image
                        src={`/images/screenshot/${imageIds[0]}`}
                        alt={`Game screenshot slide ${0}`}
                        className="w-full h-full object-cover aspect-[16/9] scale-90"
                    />
                </SwiperSlide>
            </Swiper>
        </div>
    );
}