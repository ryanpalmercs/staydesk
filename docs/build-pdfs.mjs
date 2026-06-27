#!/usr/bin/env node
// Builds PDFs from all Markdown docs under this directory.
// Images are inlined as base64 before rendering so Chromium's
// file:// restrictions don't strip them from the output.
//
// Usage (from repo root):  node docs/build-pdfs.mjs

import { readFileSync, writeFileSync, unlinkSync, readdirSync, statSync, renameSync } from 'fs';
import { join, dirname, resolve, extname } from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __dirname = dirname(fileURLToPath(import.meta.url));
const STYLESHEET = resolve(__dirname, 'style/staydesk.css');

function findMarkdown(dir) {
  const results = [];
  for (const entry of readdirSync(dir).sort()) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory() && !entry.startsWith('.')) {
      results.push(...findMarkdown(full));
    } else if (entry.endsWith('.md') && entry !== 'README.md') {
      results.push(full);
    }
  }
  return results;
}

function inlineImages(content, baseDir) {
  return content.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, (match, alt, src) => {
    if (src.startsWith('http') || src.startsWith('data:')) return match;
    try {
      const imgPath = resolve(baseDir, src);
      const ext = extname(imgPath).slice(1).toLowerCase();
      const mime = (ext === 'jpg' || ext === 'jpeg') ? 'image/jpeg' : 'image/png';
      const b64 = readFileSync(imgPath).toString('base64');
      return `![${alt}](data:${mime};base64,${b64})`;
    } catch {
      console.warn(`  warning: image not found — ${src}`);
      return match;
    }
  });
}

// Strip the 'stylesheet' key from front matter — we pass it via CLI instead.
function stripFrontmatterStylesheet(content) {
  return content.replace(/^(---[\s\S]*?)^stylesheet:.*$(\n)?/m, '$1');
}

const files = findMarkdown(__dirname);
let built = 0;

for (const file of files) {
  const rel = file.replace(__dirname + '/', '');
  const dir = dirname(file);
  const raw = readFileSync(file, 'utf8');
  const processed = stripFrontmatterStylesheet(inlineImages(raw, dir));

  const tmpMd = file.replace(/\.md$/, '._build.md');
  const tmpPdf = file.replace(/\.md$/, '._build.pdf');
  const outPdf = file.replace(/\.md$/, '.pdf');

  writeFileSync(tmpMd, processed);
  try {
    console.log(`Building ${rel}...`);
    execSync(`md-to-pdf --stylesheet "${STYLESHEET}" "${tmpMd}"`, { stdio: 'pipe' });
    renameSync(tmpPdf, outPdf);
    console.log(`  → ${rel.replace(/\.md$/, '.pdf')}`);
    built++;
  } catch (err) {
    console.error(`  ERROR: ${err.message}`);
  } finally {
    try { unlinkSync(tmpMd); } catch {}
  }
}

console.log(`\nDone — built ${built} / ${files.length} PDFs.`);
