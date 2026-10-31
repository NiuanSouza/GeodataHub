/// <reference types="vite/client" />

declare global {
  interface Window {
    __ENV__?: Record<string, string>
  }
}

export {}
