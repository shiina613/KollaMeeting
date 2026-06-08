import { readdirSync, readFileSync, statSync } from 'node:fs'
import { extname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../src', import.meta.url))
const patterns = [/Ã/, /Ä/, /Â/, /Æ/, /â€/, /áº/, /á»/, /\uFFFD/]
const extensions = new Set(['.ts', '.tsx'])
const failures = []

function walk(dir) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name)
    const stat = statSync(path)

    if (stat.isDirectory()) {
      walk(path)
      continue
    }

    if (!extensions.has(extname(path))) continue

    const lines = readFileSync(path, 'utf8').split(/\r?\n/)
    lines.forEach((line, index) => {
      if (patterns.some((pattern) => pattern.test(line))) {
        failures.push(`${path}:${index + 1}: ${line.trim()}`)
      }
    })
  }
}

walk(root)

if (failures.length > 0) {
  console.error('Possible mojibake found:')
  console.error(failures.join('\n'))
  process.exit(1)
}
