import type { Config } from 'tailwindcss'

const config: Config = {
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        'primary':                    '#005bbf',
        'primary-container':          '#1a73e8',
        'background':                 '#f7f9ff',
        'surface':                    '#f7f9ff',
        'surface-container-lowest':   '#ffffff',
        'surface-container-low':      '#f1f4fa',
        'surface-container':          '#ebeef4',
        'surface-container-high':     '#e5e8ee',
        'surface-container-highest':  '#dfe3e8',
        'on-surface':                 '#181c20',
        'on-surface-variant':         '#414754',
        'outline':                    '#727785',
        'outline-variant':            '#c1c6d6',
        'secondary':                  '#2b5bb5',
        'secondary-container':        '#759efd',
        'error':                      '#ba1a1a',
        'error-container':            '#ffdad6',
        'on-error':                   '#ffffff',
      },
      borderRadius: {
        DEFAULT: '0.125rem',  // 2px
        lg:      '0.25rem',   // 4px
        xl:      '0.5rem',    // 8px
        full:    '0.75rem',   // 12px
      },
      spacing: {
        xs:     '4px',
        sm:     '8px',
        md:     '16px',
        lg:     '24px',
        xl:     '32px',
        '2xl':  '48px',
        '3xl':  '64px',
        gutter: '24px',
      },
      fontFamily: {
        inter: ['Inter', 'system-ui', 'sans-serif'],
      },
      fontSize: {
        'body-sm': ['14px', { lineHeight: '20px', fontWeight: '400' }],
        'body-md': ['16px', { lineHeight: '24px', fontWeight: '400' }],
        'body-lg': ['18px', { lineHeight: '28px', fontWeight: '400' }],
        'button':  ['15px', { lineHeight: '20px', fontWeight: '500', letterSpacing: '0.01em' }],
        'label-md':['12px', { lineHeight: '16px', fontWeight: '600', letterSpacing: '0.05em' }],
        'h3':      ['24px', { lineHeight: '32px', fontWeight: '600' }],
        'h2':      ['32px', { lineHeight: '40px', fontWeight: '600', letterSpacing: '-0.01em' }],
        'h1':      ['40px', { lineHeight: '48px', fontWeight: '700', letterSpacing: '-0.02em' }],
      },
      width: {
        sidebar: '256px',
      },
      height: {
        header: '64px',
      },
    },
  },
  plugins: [],
}

export default config
