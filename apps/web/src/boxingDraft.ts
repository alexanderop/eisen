import type { BoxingRound } from './domain/boxing'

/**
 * Rounds that fell off the end of a boxing setup when its number went down, by setup: "mode" for
 * the mode card, a preset's id for a preset. They come back when the number goes up again, for as
 * long as the app is open. Not stored: a saved plan always has one round per round.
 */
export const keptRounds = new Map<string, BoxingRound[]>()
