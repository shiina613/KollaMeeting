import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const FORBIDDEN_USER_TEXT = [
  'Ma nhan vien',
  'Ma phong',
  'Ma phong hop',
  'Ngay sinh',
  'So dien thoai',
  'Hoc vi',
  'Dia chi',
  'Ngan hang',
  'So tai khoan',
  'Anh dai dien',
  'Ho va ten',
  'Vai tro',
  'Khong co phong ban',
  'Khong the',
  'Da cap nhat',
  'Da doi',
  'Doi mat khau',
  'Mat khau',
  'xac nhan mat khau',
  'Nguoi dung',
  'Luu ho so',
  'Trao doi',
  'Chua co tin nhan nao',
  'Thanh vien',
  'Chu tri',
  'Thu ky',
  'Phan bien',
  'Uy vien',
  'Khach moi',
]

function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry)
    if (entry === 'node_modules' || entry === 'dist' || entry === '__tests__') return []
    if (statSync(path).isDirectory()) return sourceFiles(path)
    if (/\.(test|spec)\.(tsx?|jsx?)$/.test(path)) return []
    return /\.(tsx?|jsx?)$/.test(path) ? [path] : []
  })
}

describe('Vietnamese user-facing text', () => {
  it('does not use known unaccented Vietnamese labels or messages', () => {
    const srcRoot = join(process.cwd(), 'src')
    const matches = sourceFiles(srcRoot).flatMap((file) => {
      const text = readFileSync(file, 'utf8')
      return FORBIDDEN_USER_TEXT
        .filter((phrase) => text.includes(phrase))
        .map((phrase) => `${relative(srcRoot, file)}: ${phrase}`)
    })

    expect(matches).toEqual([])
  })
})




