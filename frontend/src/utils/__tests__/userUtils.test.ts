import { describe, expect, it } from 'vitest'
import { resolveUserImageUrl } from '../userUtils'

describe('resolveUserImageUrl', () => {
  it('points API-relative avatar URLs at the configured backend origin', () => {
    expect(resolveUserImageUrl('/api/v1/users/1/avatar'))
      .toBe('http://localhost:8080/api/v1/users/1/avatar')
  })

  it('leaves browser-native and non-API relative image URLs unchanged', () => {
    expect(resolveUserImageUrl('blob:avatar-preview')).toBe('blob:avatar-preview')
    expect(resolveUserImageUrl('https://cdn.example.com/avatar.png')).toBe('https://cdn.example.com/avatar.png')
    expect(resolveUserImageUrl('/uploads/avatar.png')).toBe('/uploads/avatar.png')
  })
})
