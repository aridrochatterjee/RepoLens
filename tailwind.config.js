/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./frontend/index.html", "./frontend/js/**/*.js"],
  theme: {
    extend: {
      colors: {
        observatory: {
          graphite: "#090B12",
          surface: "#101521",
          raised: "#151C2B",
          elevated: "#1B2435",
          border: "#202A3B",
          indigo: "#7777FF",
          blue: "#529BFF",
          violet: "#A78BFA"
        }
      },
      fontFamily: {
        sans: ["Inter", "ui-sans-serif", "system-ui", "sans-serif"],
        mono: ["JetBrains Mono", "ui-monospace", "SFMono-Regular", "monospace"]
      },
      boxShadow: {
        observatory: "0 22px 60px rgb(0 0 0 / 0.34)"
      }
    }
  },
  plugins: []
};
