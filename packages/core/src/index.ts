export type SalaryType = 'monthly' | 'annual' | 'daily' | 'hourly'
export type SalaryHistoryMode = 'none' | 'month' | 'year'

/** 一周七天的看薪标记，从周一开始：[一,二,三,四,五,六,日] */
export type WeekdayFlags = [boolean, boolean, boolean, boolean, boolean, boolean, boolean]

export interface SalaryProfile {
  salary: number
  salaryType: SalaryType
  workStartTime: string
  workEndTime: string
  breakStartTime: string
  breakEndTime: string
  paidBreak: boolean
  includeLivingCost: boolean
  monthlyLivingCost: number
  monthlyWorkDays: number
  workDaysPerWeek: number
  /** 每周哪些天看薪，其余为休息日（休息日不计薪） */
  workdays: WeekdayFlags
  /** 是否把总包拆分为 五险一金 / 个税 / 到手 三条线展示 */
  deductionsEnabled: boolean
  /** 住房公积金个人比例（0.05–0.12，默认 0.12） */
  housingFundRate: number
  /** 专项附加扣除（月度，元，默认 0） */
  specialDeduction: number
  currency: string
  salaryHistoryMode: SalaryHistoryMode
  salaryEffectiveDate: string
}

export interface SalaryRates {
  daily: number
  hourly: number
  minute: number
  second: number
  paidSecondsPerDay: number
}

export const DEFAULT_WORKDAYS: WeekdayFlags = [true, true, true, true, true, false, false]

export const DEFAULT_PROFILE: SalaryProfile = {
  salary: 15000,
  salaryType: 'monthly',
  workStartTime: '09:00',
  workEndTime: '18:00',
  breakStartTime: '12:00',
  breakEndTime: '13:00',
  paidBreak: false,
  includeLivingCost: false,
  monthlyLivingCost: 0,
  monthlyWorkDays: 21.75,
  workDaysPerWeek: 5,
  workdays: DEFAULT_WORKDAYS,
  deductionsEnabled: true,
  housingFundRate: 0.12,
  specialDeduction: 0,
  currency: 'CNY',
  salaryHistoryMode: 'none',
  salaryEffectiveDate: '',
}

const DAY = 24 * 60 * 60

export function parseClock(value: string): number {
  const match = /^(\d{1,2}):(\d{2})$/.exec(value)
  if (!match) throw new Error('Invalid time format')
  const h = Number(match[1]); const m = Number(match[2])
  if (h < 0 || h > 23 || m < 0 || m > 59) throw new Error('Invalid clock time')
  return h * 3600 + m * 60
}

function duration(start: number, end: number): number {
  return end >= start ? end - start : DAY - start + end
}

function positionFromShiftStart(clock: number, shiftStart: number): number {
  return clock >= shiftStart ? clock - shiftStart : DAY - shiftStart + clock
}

export function getPaidSecondsPerDay(profile: SalaryProfile): number {
  const start = parseClock(profile.workStartTime)
  const end = parseClock(profile.workEndTime)
  const shift = duration(start, end)
  if (shift <= 0) return 0
  if (profile.paidBreak) return shift

  const breakStartClock = parseClock(profile.breakStartTime)
  const breakEndClock = parseClock(profile.breakEndTime)
  const breakStart = positionFromShiftStart(breakStartClock, start)
  const breakDuration = duration(breakStartClock, breakEndClock)
  const breakEnd = breakStart + breakDuration
  const overlap = Math.max(0, Math.min(shift, breakEnd) - Math.max(0, breakStart))
  return Math.max(0, shift - overlap)
}

export function calculateRates(profile: SalaryProfile): SalaryRates {
  if (!Number.isFinite(profile.salary) || profile.salary < 0) throw new Error('Salary must be non-negative')
  if (!Number.isFinite(profile.monthlyWorkDays) || profile.monthlyWorkDays <= 0) throw new Error('Monthly work days must be positive')
  const monthlyLivingCost = profile.includeLivingCost ? profile.monthlyLivingCost ?? 0 : 0
  if (!Number.isFinite(monthlyLivingCost) || monthlyLivingCost < 0) throw new Error('Living cost must be non-negative')

  const paidSecondsPerDay = getPaidSecondsPerDay(profile)
  if (paidSecondsPerDay <= 0) throw new Error('Paid work duration must be positive')

  const livingCostPerWorkDay = monthlyLivingCost / profile.monthlyWorkDays
  let grossDaily: number
  if (profile.salaryType === 'annual') grossDaily = (profile.salary / 12) / profile.monthlyWorkDays
  else if (profile.salaryType === 'monthly') grossDaily = profile.salary / profile.monthlyWorkDays
  else if (profile.salaryType === 'daily') grossDaily = profile.salary
  else grossDaily = profile.salary * (paidSecondsPerDay / 3600)

  const daily = Math.max(0, grossDaily - livingCostPerWorkDay)
  const second = daily / paidSecondsPerDay
  return { daily, hourly: second * 3600, minute: second * 60, second, paidSecondsPerDay }
}

function secondsSinceMidnight(date: Date): number {
  return date.getHours() * 3600 + date.getMinutes() * 60 + date.getSeconds() + date.getMilliseconds() / 1000
}

export function getWorkedPaidSeconds(profile: SalaryProfile, now = new Date()): number {
  if (isRestDay(profile, now)) return 0
  const shiftStartClock = parseClock(profile.workStartTime)
  const shiftEndClock = parseClock(profile.workEndTime)
  const shiftDuration = duration(shiftStartClock, shiftEndClock)
  const nowClock = secondsSinceMidnight(now)
  const nowPos = positionFromShiftStart(nowClock, shiftStartClock)

  const crossesMidnight = shiftEndClock < shiftStartClock
  const beforeStart = !crossesMidnight && nowClock < shiftStartClock
  const afterEnd = !crossesMidnight && nowClock >= shiftEndClock
  const inPreviousCalendarPart = crossesMidnight && nowClock < shiftEndClock
  const inShift = crossesMidnight ? (nowClock >= shiftStartClock || inPreviousCalendarPart) : (!beforeStart && !afterEnd)

  if (!inShift) {
    if (afterEnd || (crossesMidnight && nowClock >= shiftEndClock && nowClock < shiftStartClock)) return getPaidSecondsPerDay(profile)
    return 0
  }

  const elapsed = Math.min(nowPos, shiftDuration)
  if (profile.paidBreak) return elapsed

  const breakStartClock = parseClock(profile.breakStartTime)
  const breakEndClock = parseClock(profile.breakEndTime)
  const breakStart = positionFromShiftStart(breakStartClock, shiftStartClock)
  const breakDuration = duration(breakStartClock, breakEndClock)
  const breakEnd = breakStart + breakDuration
  const unpaidElapsed = Math.max(0, Math.min(elapsed, breakEnd) - Math.max(0, breakStart))
  return Math.max(0, Math.min(getPaidSecondsPerDay(profile), elapsed - unpaidElapsed))
}

export function calculateEarnedToday(profile: SalaryProfile, now = new Date()): number {
  return getWorkedPaidSeconds(profile, now) * calculateRates(profile).second
}

/** 月度总包金额（与 calculateRates 的 grossDaily 同口径反推）。 */
export function monthlyGross(profile: SalaryProfile, paidSecondsPerDay?: number): number {
  const rates = calculateRates(profile)
  const paid = paidSecondsPerDay ?? rates.paidSecondsPerDay
  let grossDaily: number
  if (profile.salaryType === 'annual') grossDaily = profile.salary / 12 / profile.monthlyWorkDays
  else if (profile.salaryType === 'monthly') grossDaily = profile.salary / profile.monthlyWorkDays
  else if (profile.salaryType === 'daily') grossDaily = profile.salary
  else grossDaily = profile.salary * (paid / 3600)
  return Math.max(0, grossDaily) * profile.monthlyWorkDays
}

/** 月度个税（月度税率表速算，未含全年累计口径，估算用）。 */
export function monthlyIncomeTax(taxable: number): number {
  const brackets: Array<[number, number, number]> = [
    [3000, 0.03, 0], [12000, 0.1, 210], [25000, 0.2, 1410], [35000, 0.25, 2660],
    [55000, 0.3, 4410], [80000, 0.35, 7160], [Infinity, 0.45, 15160],
  ]
  for (const [limit, rate, quick] of brackets) {
    if (taxable <= limit) return Math.max(0, taxable * rate - quick)
  }
  return 0
}

export interface PayBreakdown {
  grossMonthly: number
  socialInsurance: number
  housingFund: number
  incomeTax: number
  netMonthly: number
}

/**
 * 把月度总包拆成 五险一金 / 个税 / 到手。
 * 假设：社保个人 10.5%（养老 8% + 医疗 2% + 失业 0.5%），公积金比例可配，
 * 起征点 5000，专项附加扣除可配；个税用月度税率表速算，仅供参考。
 * deductionsEnabled 关闭时返回 null。
 */
export function payBreakdownMonthly(profile: SalaryProfile): PayBreakdown | null {
  if (!profile.deductionsEnabled) return null
  let rates: SalaryRates
  try {
    rates = calculateRates(profile)
  } catch {
    return null
  }
  const gross = monthlyGross(profile, rates.paidSecondsPerDay)
  const socialInsurance = gross * 0.105
  const housingFund = gross * Math.min(0.15, Math.max(0, profile.housingFundRate))
  const taxable = Math.max(0, gross - socialInsurance - housingFund - 5000 - Math.max(0, profile.specialDeduction))
  const incomeTax = monthlyIncomeTax(taxable)
  const netMonthly = Math.max(0, gross - socialInsurance - housingFund - incomeTax)
  return { grossMonthly: gross, socialInsurance, housingFund, incomeTax, netMonthly }
}

/** 本周已完成的工作日天数（周一到昨天）。 */
export function weekWorkdaysDone(profile: SalaryProfile, now = new Date()): number {
  const monday = new Date(now)
  monday.setDate(now.getDate() - ((now.getDay() + 6) % 7))
  monday.setHours(0, 0, 0, 0)
  let done = 0
  for (let day = new Date(monday); day < now; day.setDate(day.getDate() + 1)) {
    if (!isRestDay(profile, day)) done++
  }
  return done
}

/** 休息日：该天不在用户配置的工作日内，不计薪。 */
export function isRestDay(profile: SalaryProfile, date = new Date()): boolean {
  const flags = profile.workdays ?? DEFAULT_WORKDAYS
  return !flags[(date.getDay() + 6) % 7]
}

const sameCalendarDay = (a: Date, b: Date) =>
  a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()

/** 本周已入账：本周内已完成工作日的全额 + 今天（若为工作日）的实时累计。 */
export function weekEarnings(profile: SalaryProfile, now = new Date()): number {
  const rates = calculateRates(profile)
  const monday = new Date(now)
  monday.setDate(now.getDate() - ((now.getDay() + 6) % 7))
  monday.setHours(0, 0, 0, 0)
  let total = 0
  for (let day = new Date(monday); day <= now; day.setDate(day.getDate() + 1)) {
    if (isRestDay(profile, day)) continue
    const earnedSeconds = sameCalendarDay(day, now)
      ? getWorkedPaidSeconds(profile, now)
      : rates.paidSecondsPerDay
    total += earnedSeconds * rates.second
  }
  return total
}

/** 本月已入账：本月内已完成工作日的全额 + 今天（若为工作日）的实时累计。 */
export function monthEarnings(profile: SalaryProfile, now = new Date()): number {
  const rates = calculateRates(profile)
  const first = new Date(now.getFullYear(), now.getMonth(), 1)
  let total = 0
  for (let day = new Date(first); day <= now; day.setDate(day.getDate() + 1)) {
    if (isRestDay(profile, day)) continue
    const earnedSeconds = sameCalendarDay(day, now)
      ? getWorkedPaidSeconds(profile, now)
      : rates.paidSecondsPerDay
    total += earnedSeconds * rates.second
  }
  return total
}

/** 下一个工作日的上班时刻（休息日与已下班场景用于倒计时）。 */
export function nextShiftStart(profile: SalaryProfile, now = new Date()): Date {
  const [hours, minutes] = profile.workStartTime.split(':').map(Number)
  for (let offset = 0; offset <= 8; offset++) {
    const day = new Date(now)
    day.setDate(now.getDate() + offset)
    if (offset === 0 ? isRestDay(profile, day) || now.getHours() * 3600 + now.getMinutes() * 60 >= parseClock(profile.workStartTime) : isRestDay(profile, day)) continue
    day.setHours(hours, minutes, 0, 0)
    if (day > now) return day
  }
  const fallback = new Date(now)
  fallback.setDate(now.getDate() + 1)
  fallback.setHours(hours, minutes, 0, 0)
  return fallback
}

export function priceToWorkSeconds(price: number, secondRate: number): number {
  if (!Number.isFinite(price) || price < 0) throw new Error('Price must be non-negative')
  if (!Number.isFinite(secondRate) || secondRate <= 0) return Number.POSITIVE_INFINITY
  return price / secondRate
}

export function assetCostPerHour(price: number, purchaseDate: string | Date, now = new Date()): number {
  if (!Number.isFinite(price) || price < 0) throw new Error('Price must be non-negative')
  const purchase = purchaseDate instanceof Date ? purchaseDate : new Date(purchaseDate)
  const elapsedHours = (now.getTime() - purchase.getTime()) / 3_600_000
  if (!Number.isFinite(elapsedHours) || elapsedHours <= 0) return Number.POSITIVE_INFINITY
  return price / elapsedHours
}

export function slackingEarned(start: string | Date, end: string | Date, secondRate: number): number {
  const s = start instanceof Date ? start : new Date(start)
  const e = end instanceof Date ? end : new Date(end)
  const seconds = Math.max(0, (e.getTime() - s.getTime()) / 1000)
  return seconds * Math.max(0, secondRate)
}

export function formatDuration(totalSeconds: number): string {
  if (!Number.isFinite(totalSeconds)) return '∞'
  const s = Math.max(0, Math.round(totalSeconds))
  const days = Math.floor(s / 86400)
  const hours = Math.floor((s % 86400) / 3600)
  const minutes = Math.floor((s % 3600) / 60)
  const seconds = s % 60
  const parts: string[] = []
  if (days) parts.push(`${days}天`)
  if (hours) parts.push(`${hours}小时`)
  if (minutes) parts.push(`${minutes}分钟`)
  if (!days && !hours && seconds) parts.push(`${seconds}秒`)
  return parts.join('') || '0秒'
}
