import { calculateRates, DEFAULT_WORKDAYS, type SalaryProfile, type SalaryRates, type SalaryType, type WeekdayFlags } from '@salary-flow/core'
import { CheckCircle2 } from 'lucide-react'
import { FormEvent, useEffect, useState } from 'react'
import { MAX_MONEY_AMOUNT, normalizeDecimalInput, parseNumberInput, preventInvalidNumberKey } from '../lib/form'
import { loadProfile, saveProfile } from '../lib/profile'
import { fetchNativeVersion } from '../lib/widgetSync'
import './Settings.css'

const WEEKDAY_NAMES = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

function buildProfile(
  profile: SalaryProfile,
  salaryInput: string,
  monthlyLivingCostInput: string,
  monthlyWorkDaysInput: string,
  workdays: WeekdayFlags,
  deductionsEnabled: boolean,
  housingFundRateInput: string,
  specialDeductionInput: string,
): SalaryProfile | null {
  const salary = parseNumberInput(salaryInput)
  const monthlyLivingCost = parseNumberInput(monthlyLivingCostInput)
  const monthlyWorkDays = parseNumberInput(monthlyWorkDaysInput)
  const housingFundRatePercent = parseNumberInput(housingFundRateInput)
  const specialDeduction = parseNumberInput(specialDeductionInput)

  if (
    salary === null || salary < 0 || salary > MAX_MONEY_AMOUNT ||
    monthlyLivingCost === null || monthlyLivingCost < 0 || monthlyLivingCost > MAX_MONEY_AMOUNT ||
    monthlyWorkDays === null || monthlyWorkDays <= 0 || monthlyWorkDays > 31 ||
    !workdays.some(Boolean) ||
    housingFundRatePercent === null || housingFundRatePercent < 0 || housingFundRatePercent > 15 ||
    specialDeduction === null || specialDeduction < 0 || specialDeduction > MAX_MONEY_AMOUNT
  ) return null

  return {
    ...profile,
    salary,
    monthlyLivingCost,
    monthlyWorkDays,
    workDaysPerWeek: workdays.filter(Boolean).length,
    workdays,
    deductionsEnabled,
    housingFundRate: housingFundRatePercent / 100,
    specialDeduction,
  }
}

export function Settings() {
  const [initialProfile] = useState(() => loadProfile())
  const [profile, setProfile] = useState<SalaryProfile>(initialProfile)
  const [versionLabel, setVersionLabel] = useState('薪流')
  useEffect(() => {
    void fetchNativeVersion().then(v => setVersionLabel(v ? `薪流 v${v}` : '薪流 Web 版'))
  }, [])
  const [salaryInput, setSalaryInput] = useState(String(initialProfile.salary))
  const [monthlyLivingCostInput, setMonthlyLivingCostInput] = useState(String(initialProfile.monthlyLivingCost))
  const [monthlyWorkDaysInput, setMonthlyWorkDaysInput] = useState(String(initialProfile.monthlyWorkDays))
  const [workdays, setWorkdays] = useState<WeekdayFlags>(() => initialProfile.workdays ?? [...DEFAULT_WORKDAYS])
  const [deductionsEnabled, setDeductionsEnabled] = useState(initialProfile.deductionsEnabled ?? true)
  const [housingFundRateInput, setHousingFundRateInput] = useState(String(Math.round((initialProfile.housingFundRate ?? 0.12) * 100)))
  const [specialDeductionInput, setSpecialDeductionInput] = useState(String(initialProfile.specialDeduction ?? 0))
  const [saved, setSaved] = useState(false)

  const draftProfile = buildProfile(profile, salaryInput, monthlyLivingCostInput, monthlyWorkDaysInput, workdays, deductionsEnabled, housingFundRateInput, specialDeductionInput)
  let rates: SalaryRates | null = null
  let calculationError = ''
  if (draftProfile) {
    try {
      rates = calculateRates(draftProfile)
    } catch {
      calculationError = '有效计薪时长必须大于 0，请检查上下班时间和午休设置。'
    }
  }

  const set = <K extends keyof SalaryProfile>(key: K, value: SalaryProfile[K]) => {
    setSaved(false)
    setProfile(current => ({ ...current, [key]: value }))
  }

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!event.currentTarget.reportValidity() || !draftProfile || !rates) return
    saveProfile(draftProfile)
    setProfile(draftProfile)
    setSaved(true)
  }

  const toggleWorkday = (index: number) => {
    setSaved(false)
    setWorkdays(current => current.map((value, i) => (i === index ? !value : value)) as WeekdayFlags)
  }

  const rateLabelPrefix = profile.includeLivingCost ? '可支配' : ''
  return <section className="page"><header className="page-header"><div><p className="eyebrow">SALARY PROFILE</p><h1>先定义，你的一小时值多少钱。</h1><p>这里输入的是“用于时间价值计算的工资”，MVP 不负责个税、社保或不同国家税制。</p></div></header>
    <form className="settings-card" onSubmit={submit}><div className="settings-section"><h3>工资</h3><div className="form-grid"><label><span>工资金额</span><input required type="number" inputMode="decimal" min="0" max={MAX_MONEY_AMOUNT} step="0.01" value={salaryInput} onKeyDown={preventInvalidNumberKey} onChange={event=>{setSaved(false);setSalaryInput(normalizeDecimalInput(event.target.value))}}/></label><label><span>工资周期</span><select required value={profile.salaryType} onChange={event=>set('salaryType',event.target.value as SalaryType)}><option value="monthly">月薪</option><option value="annual">年薪</option><option value="daily">日薪</option><option value="hourly">时薪</option></select></label><label><span>月平均工作日</span><input required type="number" inputMode="decimal" min="0.01" max="31" step="0.01" value={monthlyWorkDaysInput} onKeyDown={preventInvalidNumberKey} onChange={event=>{setSaved(false);setMonthlyWorkDaysInput(normalizeDecimalInput(event.target.value))}}/></label></div><label className="toggle-row"><input type="checkbox" checked={deductionsEnabled} onChange={event=>{setSaved(false);setDeductionsEnabled(event.target.checked)}}/><span><b>拆分五险一金与个税</b><small>按社保个人 10.5%、公积金与专项附加扣除估算，把今日已赚拆成到手、五险一金和个税三条线</small></span></label>{deductionsEnabled&&<div className="form-grid living-cost-field"><label><span>公积金比例（%）</span><input required type="number" inputMode="decimal" min="0" max="15" step="0.5" value={housingFundRateInput} onKeyDown={preventInvalidNumberKey} onChange={event=>{setSaved(false);setHousingFundRateInput(normalizeDecimalInput(event.target.value))}}/></label><label><span>专项附加扣除（元/月）</span><input required type="number" inputMode="decimal" min="0" max={MAX_MONEY_AMOUNT} step="100" value={specialDeductionInput} onKeyDown={preventInvalidNumberKey} onChange={event=>{setSaved(false);setSpecialDeductionInput(normalizeDecimalInput(event.target.value))}}/></label></div>}<label className="toggle-row"><input type="checkbox" checked={profile.includeLivingCost} onChange={event=>set('includeLivingCost',event.target.checked)}/><span><b>计算生活成本</b><small>开启后，先扣除月生活成本，再计算可支配日薪、时薪、<span className="nowrap">分钟薪资和秒薪。</span></small></span></label>{profile.includeLivingCost&&<div className="form-grid living-cost-field"><label><span>月生活成本</span><div className="money-input"><i>¥</i><input required type="number" inputMode="decimal" min="0" max={MAX_MONEY_AMOUNT} step="0.01" value={monthlyLivingCostInput} onKeyDown={preventInvalidNumberKey} onChange={event=>{setSaved(false);setMonthlyLivingCostInput(normalizeDecimalInput(event.target.value))}} placeholder="例如：5000"/></div></label></div>}</div>
      <div className="settings-section"><h3>工作时间</h3><div className="form-grid"><label><span>上班时间</span><input required type="time" value={profile.workStartTime} onChange={event=>set('workStartTime',event.target.value)}/></label><label><span>下班时间</span><input required type="time" value={profile.workEndTime} onChange={event=>set('workEndTime',event.target.value)}/></label><label><span>午休开始</span><input required type="time" value={profile.breakStartTime} onChange={event=>set('breakStartTime',event.target.value)}/></label><label><span>午休结束</span><input required type="time" value={profile.breakEndTime} onChange={event=>set('breakEndTime',event.target.value)}/></label></div><div className="workday-block"><span className="workday-title">工作日</span><div className="workday-picker" role="group" aria-label="每周工作日">{WEEKDAY_NAMES.map((name, index) => <button type="button" key={name} className={workdays[index]?'workday-chip active':'workday-chip'} aria-pressed={workdays[index]} onClick={()=>toggleWorkday(index)}>{name}</button>)}</div><small className="workday-hint">只有勾选的日子会计薪；休息日小组件会切换为专属内容。</small>{!workdays.some(Boolean)&&<p className="settings-warning" role="alert">至少选择一个工作日，否则无法计薪。</p>}</div><label className="toggle-row"><input type="checkbox" checked={profile.paidBreak} onChange={event=>set('paidBreak',event.target.checked)}/><span><b>午休计薪</b><small>开启后，午休时间也会计入今日实时收入</small></span></label></div>
      {rates&&<div className="rate-preview"><div><small>{rateLabelPrefix}日薪</small><b>¥{rates.daily.toFixed(2)}</b></div><div><small>{rateLabelPrefix}时薪</small><b>¥{rates.hourly.toFixed(2)}</b></div><div><small>{rateLabelPrefix}分钟</small><b>¥{rates.minute.toFixed(3)}</b></div><div><small>{rateLabelPrefix}每秒</small><b>¥{rates.second.toFixed(5)}</b></div></div>}
      {calculationError&&<p className="settings-warning" role="alert">{calculationError}</p>}
      {draftProfile&&draftProfile.includeLivingCost&&draftProfile.monthlyLivingCost>draftProfile.salary&&draftProfile.salaryType==='monthly'&&<p className="settings-warning">生活成本高于月薪，当前可支配薪资会按 0 计算。</p>}
      <button className="primary-button" type="submit">{saved?<><CheckCircle2 size={17}/>已保存</>: '保存薪资设置'}</button></form><p className="version-note">{versionLabel}</p>
  </section>
}
