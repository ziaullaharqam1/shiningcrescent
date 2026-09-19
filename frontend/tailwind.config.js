/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        grove: {
          900: "var(--rc-900)",
          800: "var(--rc-800)",
          700: "var(--rc-700)",
          600: "var(--rc-600)",
          500: "var(--rc-500)",
          100: "var(--rc-100)",
          50: "var(--rc-50)",
        },
        steel: {
          900: "#1E3A4C",
          800: "#2E5A7A",
          600: "#3D6B8C",
          500: "#4682B4",
          100: "#D6E6F0",
          50: "#EEF5FA",
        },
        apricot: {
          600: "#ea580c",
          500: "#f97316",
          100: "#ffedd5",
        },
        gold: {
          500: "#eab308",
          100: "#fef9c3",
        },
        cream: "#ffffff",
        paper: "#ffffff",
        peach: "#ffedd5",
        sky: "#e0f2fe",
        citrus: "#fde68a",
      },
      fontFamily: {
        display: ["Fraunces", "Georgia", "serif"],
        sans: ["Manrope", "system-ui", "sans-serif"],
      },
      boxShadow: {
        soft: "0 12px 32px -16px rgba(22, 101, 52, 0.28)",
      },
    },
  },
  plugins: [],
};
