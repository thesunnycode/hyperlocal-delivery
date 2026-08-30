import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';

// Shared between the JS and TS blocks so the two stay in step while the
// TypeScript migration is in flight (see TS-MIGRATION-PROMPT.md).
const languageOptions = {
  ecmaVersion: 'latest',
  sourceType: 'module',
  parserOptions: {
    ecmaFeatures: { jsx: true },
  },
  globals: {
    window: 'readonly',
    document: 'readonly',
    navigator: 'readonly',
    localStorage: 'readonly',
    fetch: 'readonly',
    URL: 'readonly',
    Blob: 'readonly',
    setTimeout: 'readonly',
    clearTimeout: 'readonly',
    console: 'readonly',
  },
};

export default [
  {
    files: ['src/**/*.{js,jsx}'],
    plugins: {
      'react-hooks': reactHooks,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      'no-console': ['warn', { allow: ['warn', 'error'] }],
    },
    languageOptions,
  },

  // TypeScript sources — same rule intent as the JS block above, with
  // no-unused-vars handed over to the typescript-eslint version (the core
  // rule misreports on type-only identifiers).
  ...tseslint.configs.recommended.map((config) => ({
    ...config,
    files: ['src/**/*.{ts,tsx}'],
  })),
  {
    files: ['src/**/*.{ts,tsx}'],
    plugins: {
      'react-hooks': reactHooks,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'no-unused-vars': 'off',
      '@typescript-eslint/no-unused-vars': [
        'warn',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
      'no-console': ['warn', { allow: ['warn', 'error'] }],
    },
    languageOptions: {
      ...languageOptions,
      parserOptions: {
        ...languageOptions.parserOptions,
        projectService: true,
      },
    },
  },

  {
    ignores: ['node/', 'node_modules/', 'dist/'],
  },
];
