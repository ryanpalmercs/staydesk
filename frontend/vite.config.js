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

// The git tag pointing exactly at this build's commit (e.g. "v1.4.1" or "1.4.0" - whatever the
// tag literally says, no normalizing), used as a fallback display label for release notes entries
// that don't hand-set their own `version`. --exact-match means this only resolves on a build made
// right at a tagged commit (production, immediately after tagging) - everything else (feature
// branches, beta/develop trailing behind the latest tag, a shallow Vercel clone with no tag
// history) quietly resolves to null and the version line is omitted, rather than showing a messy
// "1.3.0-15-gabc1234"-style description.
function resolveAppTag() {
    try {
        return execSync('git describe --tags --exact-match 2>/dev/null').toString().trim()
    } catch {
        return null
    }
}

export default defineConfig({
    plugins: [react(), tailwindcss(), basicSsl()],
    define: {
        __APP_VERSION__: JSON.stringify(resolveAppVersion()),
        __APP_TAG__: JSON.stringify(resolveAppTag())
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