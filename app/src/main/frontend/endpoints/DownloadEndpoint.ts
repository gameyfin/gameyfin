export function downloadGame(gameId: number, provider: string) {
    window.open(`/download/${gameId}?provider=${provider}`, '_top');
}