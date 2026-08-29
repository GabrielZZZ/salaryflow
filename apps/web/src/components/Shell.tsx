import { Boxes, ChevronUp, CircleDollarSign, Clock3, Coins, Fish, Settings2 } from 'lucide-react'
import { useEffect, useRef, useState, type TouchEvent } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import './Shell.css'

const items = [
  ['/', Coins, '今日', true],
  ['/convert', Clock3, '换算', true],
  ['/accidents', CircleDollarSign, '意外', false],
  ['/slacking', Fish, '摸鱼', false],
  ['/assets', Boxes, '物品', false],
  ['/settings', Settings2, '我的', true],
] as const

  const compactItems = items.filter(([, , , compact]) => compact)
  const drawerItems = items.filter(([, , , compact]) => !compact)

export function Shell() {
  const location = useLocation()
  const [mobileOpen, setMobileOpen] = useState(false)
  const touchStartY = useRef<number | null>(null)
  const mobileHandleRef = useRef<HTMLButtonElement>(null)
  const mobilePreviousFocusRef = useRef<HTMLElement | null>(null)
  const mobileWasOpenRef = useRef(false)
  const activeDrawerItem = drawerItems.find(([to]) => location.pathname === to)

  const setMobileNavigationOpen = (open: boolean) => {
    if (open && !mobileOpen) {
      const activeElement = document.activeElement
      mobilePreviousFocusRef.current = activeElement instanceof HTMLElement && activeElement !== document.body ? activeElement : null
    }
    setMobileOpen(open)
  }

  useEffect(() => {
    setMobileOpen(false)
    window.scrollTo(0, 0)
  }, [location.pathname])

  useEffect(() => {
    if (mobileOpen) {
      mobileWasOpenRef.current = true
      return
    }
    if (!mobileWasOpenRef.current) return
    const previousFocus = mobilePreviousFocusRef.current
    mobilePreviousFocusRef.current = null
    mobileWasOpenRef.current = false
    if (previousFocus?.isConnected) previousFocus.focus()
    else mobileHandleRef.current?.focus()
  }, [mobileOpen])

  const onTouchStart = (event: TouchEvent) => {
    touchStartY.current = event.touches[0]?.clientY ?? null
  }

  const onTouchEnd = (event: TouchEvent) => {
    if (touchStartY.current === null) return
    const endY = event.changedTouches[0]?.clientY ?? touchStartY.current
    const delta = endY - touchStartY.current
    touchStartY.current = null
    if (delta < -24) setMobileNavigationOpen(true)
    if (delta > 24) setMobileNavigationOpen(false)
  }

  const renderItem = ([to, Icon, label]: (typeof items)[number], drawer = false) => (
    <NavLink
      key={`${drawer ? 'drawer' : 'compact'}-${to}`}
      to={to}
      end={to === '/'}
      onClick={() => setMobileOpen(false)}
      className={({ isActive }) => `${drawer ? 'mobile-drawer-item' : 'mobile-dock-item'}${isActive ? ' active' : ''}`}
    >
      <Icon size={drawer ? 19 : 18}/><span>{label}</span>
    </NavLink>
  )

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><span className="brand-mark">¥</span><div><b>SalaryFlow</b><small>薪流</small></div></div>
      <nav>{items.map(([to, Icon, label]) => <NavLink key={to} to={to} end={to === '/'} className={({isActive}) => isActive ? 'nav-item active' : 'nav-item'}><Icon size={19}/><span>{label}</span></NavLink>)}</nav>
      <p className="privacy-note">Local-first · 薪资默认只保存在你的浏览器</p>
    </aside>
    <main className="main"><div className="route-stage" key={location.pathname}><Outlet /></div></main>

    <div className={`mobile-nav-layer${mobileOpen ? ' open' : ''}`}>
      <button className="mobile-nav-backdrop" type="button" disabled={!mobileOpen} tabIndex={mobileOpen ? 0 : -1} aria-hidden={!mobileOpen} inert={!mobileOpen} aria-label="收起导航" onClick={() => setMobileOpen(false)}/>
      <nav className="mobile-dock" aria-label="移动端主导航" onTouchStart={onTouchStart} onTouchEnd={onTouchEnd}>
        <button
          ref={mobileHandleRef}
          type="button"
          className={`mobile-dock-handle${activeDrawerItem ? ' has-active' : ''}`}
          data-active-label={activeDrawerItem?.[2]}
          aria-label={mobileOpen ? '收起更多导航' : activeDrawerItem ? `展开更多导航，当前页面：${activeDrawerItem[2]}` : '展开更多导航'}
          aria-expanded={mobileOpen}
          onClick={() => setMobileNavigationOpen(!mobileOpen)}
        >
          <ChevronUp size={12}/><span className="mobile-dock-handle-indicator" aria-hidden="true"/>
        </button>
        <div className="mobile-drawer" aria-hidden={!mobileOpen} inert={!mobileOpen}>
          <div className="mobile-drawer-grid">{drawerItems.map(item => renderItem(item, true))}</div>
        </div>
        <div className="mobile-dock-row">{compactItems.map(item => renderItem(item))}</div>
      </nav>
    </div>
  </div>
}
