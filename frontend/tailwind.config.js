/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        grove: {
          900: "#0e3922",
          800: "#0f5132",
          700: "#15803d",
          600: "#0f5132",
          500: "#16a34a",
          400: "#4ade80",
          100: "#ecfdf5",
          50: "#f8faf9",
        },
        primary: {
          DEFAULT: "#0f5132",
          container: "#0b3d26",
          soft: "#e8f5ee",
        },
        secondary: {
          DEFAULT: "#15803d",
          container: "#ecfdf5",
          fixed: "#d1fae5",
          ink: "#065f46",
        },
        surface: {
          DEFAULT: "#fafaf9",
          card: "#ffffff",
          low: "#f5f5f4",
          mid: "#f1f5f3",
          bright: "#ffffff",
        },
        ink: {
          DEFAULT: "#111827",
          muted: "#6b7280",
          faint: "#9ca3af",
        },
        line: {
          DEFAULT: "#e7e5e4",
          strong: "#d6d3d1",
        },
        harvest: {
          amber: "#d97706",
          soft: "#fef3c7",
          ink: "#92400e",
        },
        danger: {
          DEFAULT: "#dc2626",
          soft: "#fee2e2",
          ink: "#991b1b",
        },
        steel: {
          900: "#1E3A4C",
          800: "#2E5A7A",
          600: "#3D6B8C",
          500: "#4682B4",
          100: "#D6E6F0",
          50: "#EEF5FA",
        },
        peach: "#fff7ed",
        citrus: "#fef9c3",
        sky: "#eef5fa",
        gold: {
          100: "#fef3c7",
        },
        cream: "#fafaf9",
        paper: "#ffffff",
      },
      fontFamily: {
        display: ['"Playfair Display"', "Georgia", "serif"],
        sans: ['"Plus Jakarta Sans"', "system-ui", "sans-serif"],
      },
      borderRadius: {
        DEFAULT: "0.5rem",
      },
      boxShadow: {
        soft: "0 1px 3px rgba(15, 81, 50, 0.04), 0 1px 2px rgba(0, 0, 0, 0.02)",
        lift: "0 8px 20px -4px rgba(15, 81, 50, 0.08), 0 4px 6px -2px rgba(0, 0, 0, 0.03)",
        float: "0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)",
      },
      maxWidth: {
        frame: "80rem",
      },
    },
  },
  plugins: [],
};
