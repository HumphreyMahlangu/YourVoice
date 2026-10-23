const DEFAULT_API_BASE_URL = import.meta.env.PROD
  ? window.location.origin
  : 'http://localhost:8080'

const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()

export const API_BASE_URL = (
  configuredApiBaseUrl || DEFAULT_API_BASE_URL
).replace(/\/+$/, '')
