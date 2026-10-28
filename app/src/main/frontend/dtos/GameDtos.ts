import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";

export interface GameAdminDto extends GameDto {
    metadata: GameMetadataAdminDto;
}

export interface GameMetadataAdminDto {
    path?: string | null;
    fileSize: number;
    fields?: { [key: string]: GameFieldMetadataDto } | null;
    originalIds?: { [key: string]: string } | null;
    downloadCount: number;
    matchConfirmed: boolean;
}

export interface GameFieldMetadataDto {
    type: GameFieldMetadataType;
    source: string;
    updatedAt: string;
}

export enum GameFieldMetadataType {
    PLUGIN = 'PLUGIN',
    USER = 'USER',
    UNKNOWN = 'UNKNOWN'
}