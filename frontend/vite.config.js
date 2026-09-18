import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import basicSsl from '@vitejs/plugin-basic-ssl'
import { execSync } from 'node:child_process'

// Vercel sets this automatically during a deploy build; falls back to reading the local git HEAD
// so a plain `npm run build` still produces a real, comparable version identifier.
function resolveAppVersion() {
    if (process.env.VERCEL_GIT_COMMIT_SHA) {
        return process.env.VERCEL_GIT_COMMIT_SHA
    }
    try {
        return execSync('git rev-parse HEAD').toString().trim()
    } catch {
        return 'dev'
    }
}

export default defineConfig({
    plugins: [react(), tailwindcss(), basicSsl()],
    define: {
        __APP_VERSION__: JSON.stringify(resolveAppVersion())
    },
    server: {
        port: 5174,
        https: true,
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                rewrite: (path) => path.replace(/^\/api/, '')
            }
        }
    }
})