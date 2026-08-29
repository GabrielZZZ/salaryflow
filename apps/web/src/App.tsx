import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { Shell } from './components/Shell'
import { Accidents } from './pages/Accidents'
import { Assets } from './pages/Assets'
import { Converter } from './pages/Converter'
import { Dashboard } from './pages/Dashboard'
import { Settings } from './pages/Settings'
import { Slacking } from './pages/Slacking'

export default function App(){return <BrowserRouter><Routes><Route element={<Shell/>}><Route path="/" element={<Dashboard/>}/><Route path="/convert" element={<Converter/>}/><Route path="/accidents" element={<Accidents/>}/><Route path="/slacking" element={<Slacking/>}/><Route path="/assets" element={<Assets/>}/><Route path="/settings" element={<Settings/>}/><Route path="*" element={<Navigate to="/" replace/>}/></Route></Routes></BrowserRouter>}
