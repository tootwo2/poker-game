export function callAmount(table, player) {
  return Math.max(0, table.currentBet - (player?.streetBet || 0))
}

export function canStart(table) {
  return ['WAITING', 'SHOWDOWN'].includes(table.phase) && table.players.length >= 2
}

export function waitingForNextHand(table, player) {
  return !!table && !['WAITING', 'SHOWDOWN'].includes(table.phase) && player?.status === 'SITTING'
}

export function minimumRaiseTo(table) {
  return table.currentBet + table.minRaise
}

export function validRaise(table, player, raiseTo) {
  return Number.isFinite(raiseTo)
    && player?.canRaise !== false
    && raiseTo >= minimumRaiseTo(table)
    && raiseTo - player.streetBet < player.chips
}

export function canAllIn(table, player) {
  const allInTo = (player?.streetBet || 0) + (player?.chips || 0)
  return (player?.chips || 0) > 0 && (allInTo <= table.currentBet || player?.canRaise !== false)
}

export function quickRaiseTo(table, player, fraction) {
  const minimum = minimumRaiseTo(table)
  const maximum = (player?.streetBet || 0) + (player?.chips || 0) - 1
  if (player?.canRaise === false || maximum < minimum) return null
  const outstanding = callAmount(table, player)
  const potAfterCall = (table.pot || 0) + outstanding
  const step = Math.max(1, table.bigBlind || 1)
  const extra = Math.ceil((potAfterCall * fraction) / step) * step
  const target = (player?.streetBet || 0) + outstanding + Math.max(step, extra)
  return Math.min(maximum, Math.max(minimum, target))
}


