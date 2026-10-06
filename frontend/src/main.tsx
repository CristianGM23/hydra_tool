import './monacoBootstrap'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { AppearanceSettingsProvider } from './settings/AppearanceSettingsContext'
import { HydraDomTranslator, HydraLanguageProvider } from './i18n'
import { HydraFormHardening } from './security/HydraFormHardening'
import { HydraPerformanceReporter } from './diagram/HydraPerformanceReporter'
import './i18n/i18n.css'
import './settings/settings.css'
import './design-system/hydra-design-system.css'
import './design-system/hydra-form-density.css'
import './design-system/hydra-theme.css'
import './design-system/hydra-accessibility.css'
import './design-system/hydra-dark-theme-convergence.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <HydraLanguageProvider>
      <AppearanceSettingsProvider>
        <HydraDomTranslator />
        <HydraFormHardening />
        <HydraPerformanceReporter />
        <App />
      </AppearanceSettingsProvider>
    </HydraLanguageProvider>
  </StrictMode>,
)
