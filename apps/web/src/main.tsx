import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'
import { loadProfile } from './lib/profile'
import { syncWidgetProfile } from './lib/widgetSync'
import './styles.css'
import './mobile.css'

createRoot(document.getElementById('root')!).render(<StrictMode><App/></StrictMode>)

const isNativeShell = 'Capacitor' in window
if (isNativeShell) syncWidgetProfile(loadProfile())
const isLocalDev = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1'
if ('serviceWorker' in navigator && !isNativeShell && !isLocalDev) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => undefined)
  })
}
