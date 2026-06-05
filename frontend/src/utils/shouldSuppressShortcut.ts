/**
 * shouldSuppressShortcut — determines whether a keyboard shortcut should be suppressed
 * based on the currently focused element.
 *
 * Returns true if `document.activeElement` is an `<input>`, `<textarea>`, or an element
 * with `contenteditable="true"`. This prevents shortcuts from firing while the user
 * is typing in a text field.
 *
 * Requirements: 7.4
 */

export function shouldSuppressShortcut(): boolean {
  const activeElement = document.activeElement

  if (!activeElement) {
    return false
  }

  const tagName = activeElement.tagName.toLowerCase()

  if (tagName === 'input' || tagName === 'textarea') {
    return true
  }

  if (activeElement.getAttribute('contenteditable') === 'true') {
    return true
  }

  return false
}
