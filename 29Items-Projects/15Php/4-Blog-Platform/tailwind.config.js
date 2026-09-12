import defaultTheme from 'tailwindcss/defaultTheme';
import typography from '@tailwindcss/typography';

/** @type {import('tailwindcss').Config} */
export default {
    // `class` strategy enables the dark-mode toggle (Alpine persists the choice).
    darkMode: 'class',
    content: [
        './storage/framework/views/*.php',
        './resources/views/**/*.blade.php',
        // Livewire components may emit class names from PHP — keep them in scope.
        './app/Livewire/**/*.php',
        './app/View/**/*.php',
    ],
    theme: {
        extend: {
            fontFamily: {
                sans: ['Figtree', ...defaultTheme.fontFamily.sans],
            },
            typography: {
                // Tuning hook for the `prose` classes used to render post bodies.
                DEFAULT: {
                    css: {
                        'code::before': { content: '""' },
                        'code::after': { content: '""' },
                    },
                },
            },
        },
    },
    plugins: [
        typography, // styles the sanitised Markdown HTML via `prose`
    ],
};
