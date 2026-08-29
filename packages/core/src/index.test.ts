import { describe, expect, it } from 'vitest'
import { DEFAULT_PROFILE, assetCostPerHour, calculateEarnedToday, calculateRates, getWorkedPaidSeconds, isRestDay, monthEarnings, monthlyIncomeTax, nextShiftStart, payBreakdownMonthly, priceToWorkSeconds, weekWorkdaysDone, slackingEarned, weekEarnings } from './index.js'

describe('salary calculations', () => {
  it('calculates an 8-hour paid work day after unpaid lunch', () => {
    const rates = calculateRates(DEFAULT_PROFILE)
    expect(rates.paidSecondsPerDay).toBe(8 * 3600)
    expect(rates.daily).toBeCloseTo(15000 / 21.75, 8)
  })

  it('subtracts monthly living cost before deriving rates', () => {
    const rates = calculateRates({ ...DEFAULT_PROFILE, includeLivingCost: true, monthlyLivingCost: 3000 })
    expect(rates.daily).toBeCloseTo((15000 - 3000) / 21.75, 8)
    expect(rates.hourly).toBeCloseTo(((15000 - 3000) / 21.75) / 8, 8)
  })

  it('never returns negative disposable rates when living cost exceeds income', () => {
    const rates = calculateRates({ ...DEFAULT_PROFILE, includeLivingCost: true, monthlyLivingCost: 20000 })
    expect(rates.daily).toBe(0)
    expect(rates.second).toBe(0)
  })

  it('does not count unpaid lunch as worked time', () => {
    const now = new Date(2026, 7, 25, 12, 30, 0)
    expect(getWorkedPaidSeconds(DEFAULT_PROFILE, now)).toBe(3 * 3600)
  })

  it('caps earnings after work ends', () => {
    const now = new Date(2026, 7, 25, 23, 0, 0)
    expect(getWorkedPaidSeconds(DEFAULT_PROFILE, now)).toBe(8 * 3600)
  })

  it('converts price to required work time', () => {
    expect(priceToWorkSeconds(100, 2)).toBe(50)
  })

  it('computes slacking earnings', () => {
    expect(slackingEarned('2026-08-25T10:00:00Z', '2026-08-25T10:10:00Z', 0.5)).toBe(300)
  })

  it('computes ownership cost', () => {
    expect(assetCostPerHour(240, new Date('2026-08-24T00:00:00Z'), new Date('2026-08-25T00:00:00Z'))).toBe(10)
  })
})

describe('workday configuration and rest days', () => {
  it('does not accrue salary on configured rest days', () => {
    const saturdayNoon = new Date(2026, 7, 29, 12, 0, 0)
    expect(isRestDay(DEFAULT_PROFILE, saturdayNoon)).toBe(true)
    expect(getWorkedPaidSeconds(DEFAULT_PROFILE, saturdayNoon)).toBe(0)
    expect(calculateEarnedToday(DEFAULT_PROFILE, saturdayNoon)).toBe(0)
  })

  it('accrues normally when a weekend day is configured as a workday', () => {
    const sixDayWeek = { ...DEFAULT_PROFILE, workdays: [true, true, true, true, true, true, false] as [boolean, boolean, boolean, boolean, boolean, boolean, boolean] }
    const saturdayTen = new Date(2026, 7, 29, 10, 0, 0)
    expect(isRestDay(sixDayWeek, saturdayTen)).toBe(false)
    expect(getWorkedPaidSeconds(sixDayWeek, saturdayTen)).toBe(3600)
  })

  it('weekEarnings counts completed workdays of the current week', () => {
    const sundayNight = new Date(2026, 7, 30, 20, 0, 0)
    const rates = calculateRates(DEFAULT_PROFILE)
    expect(weekEarnings(DEFAULT_PROFILE, sundayNight)).toBeCloseTo(rates.daily * 5, 8)
  })

  it('monthEarnings accumulates completed workdays of the month', () => {
    const saturdayMorning = new Date(2026, 7, 29, 8, 0, 0)
    const rates = calculateRates(DEFAULT_PROFILE)
    expect(monthEarnings(DEFAULT_PROFILE, saturdayMorning)).toBeCloseTo(rates.daily * 20, 8)
  })

  it('nextShiftStart lands on the next configured workday start', () => {
    const saturdayNight = new Date(2026, 7, 29, 22, 0, 0)
    const next = nextShiftStart(DEFAULT_PROFILE, saturdayNight)
    expect(next.getDate()).toBe(31)
    expect(next.getHours()).toBe(9)
  })
})


describe('pay breakdown (social insurance, housing fund, tax)', () => {
  it('splits a 21000 gross month into social, fund, tax and net', () => {
    const profile = { ...DEFAULT_PROFILE, salary: 21000, deductionsEnabled: true, housingFundRate: 0.12 }
    const b = payBreakdownMonthly(profile)
    expect(b).not.toBeNull()
    expect(b!.grossMonthly).toBeCloseTo(21000, 6)
    expect(b!.socialInsurance).toBeCloseTo(2205, 6)
    expect(b!.housingFund).toBeCloseTo(2520, 6)
    // taxable = 21000 - 2205 - 2520 - 5000 = 11275 -> 10% bracket
    expect(b!.incomeTax).toBeCloseTo(11275 * 0.1 - 210, 6)
    expect(b!.netMonthly).toBeCloseTo(21000 - 2205 - 2520 - 917.5, 6)
  })

  it('returns null when deductions are disabled', () => {
    expect(payBreakdownMonthly({ ...DEFAULT_PROFILE, deductionsEnabled: false })).toBeNull()
  })

  it('weekWorkdaysDone counts completed workdays by configured week', () => {
    const saturday = new Date(2026, 7, 29, 12, 0, 0)
    expect(weekWorkdaysDone(DEFAULT_PROFILE, saturday)).toBe(5)
  })

  it('monthly income tax follows the bracket table', () => {
    expect(monthlyIncomeTax(0)).toBe(0)
    expect(monthlyIncomeTax(3000)).toBeCloseTo(90, 6)
    expect(monthlyIncomeTax(11275)).toBeCloseTo(917.5, 6)
  })
})
