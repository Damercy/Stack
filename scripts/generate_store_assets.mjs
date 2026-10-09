import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "..");
const outDir = path.join(repoRoot, "store-assets", "play");
const nodeModules =
  process.env.STACK_NODE_MODULES ||
  process.env.STACK_NODE_MODULES ?? "node_modules";
const playwrightRequireRoot = path.join(
  nodeModules,
  ".pnpm",
  "playwright@1.61.1",
  "node_modules",
  "playwright",
  "package.json",
);
const require = createRequire(playwrightRequireRoot);
const { chromium } = require("playwright");

await fs.mkdir(outDir, { recursive: true });

const phoneScreens = [
  {
    file: "phone-01-tap-screen.png",
    title: "12,847",
    subtitle: "",
    footer: "",
    caption: "Tap. Stack. Repeat.",
  },
  {
    file: "phone-02-away-earnings.png",
    title: "48,372",
    subtitle: "You earned 847 taps while away",
    footer: "",
    caption: "Auto-Miner keeps stacking",
  },
  {
    file: "phone-03-leaderboard.png",
    leaderboard: true,
    caption: "Today's Tappers",
  },
  {
    file: "phone-04-display-name.png",
    displayName: true,
    caption: "Pick your name",
  },
  {
    file: "phone-05-subscription-prompt.png",
    title: "25,000",
    subtitle: "",
    footer: "Stack taps while you sleep",
    button: "Subscribe - INR 49/month",
    caption: "Auto-Miner is optional",
  },
  {
    file: "phone-06-auto-miner-active.png",
    title: "62,911",
    subtitle: "",
    footer: "Auto-Miner active",
    button: "Manage Auto-Miner",
    caption: "Control your subscription",
  },
];

const tabletScreens = [
  {
    file: "tablet-7-01-tap-screen.png",
    title: "12,847",
    caption: "Tap. Stack. Repeat.",
  },
  {
    file: "tablet-7-02-leaderboard.png",
    leaderboard: true,
    caption: "Today's Tappers",
  },
  {
    file: "tablet-10-01-tap-screen.png",
    title: "48,372",
    subtitle: "You earned 847 taps while away",
    caption: "Auto-Miner keeps stacking",
  },
  {
    file: "tablet-10-02-subscription-prompt.png",
    title: "25,000",
    footer: "Stack taps while you sleep",
    button: "Subscribe - INR 49/month",
    caption: "Auto-Miner is optional",
  },
];

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function appScreenHtml(screen, width = 1080, height = 1920) {
  const rows = [
    ["#1", "tapgod42", "54,120"],
    ["#2", "stacklord", "49,870"],
    ["#3", "Player", "48,372"],
    ["#4", "numbermax", "31,420"],
    ["#5", "sleepTapper", "25,014"],
    ["#6", "whitecount", "19,800"],
  ];

  let content = "";
  if (screen.leaderboard) {
    content = `
      <section class="leaderboard">
        <h1>Today's Tappers</h1>
        <div class="rows">
          ${rows
            .map(
              ([rank, name, count], index) => `
                <div class="row ${index === 2 ? "me" : ""}">
                  <span>${rank}</span>
                  <strong>${name}</strong>
                  <span>${count}</span>
                </div>`,
            )
            .join("")}
        </div>
        <p class="you">You: Player - 48,372 taps today</p>
      </section>`;
  } else if (screen.displayName) {
    content = `
      <section class="namePrompt">
        <h1>Today's Tappers</h1>
        <label>Pick a display name</label>
        <div class="input">tapgod42</div>
        <div class="save">Save</div>
        <p class="you">You: unnamed - 1,284 taps today</p>
      </section>`;
  } else {
    content = `
      <section class="tap">
        <div class="count">${escapeHtml(screen.title)}</div>
        ${screen.subtitle ? `<p class="away">${escapeHtml(screen.subtitle)}</p>` : ""}
        ${
          screen.footer
            ? `<div class="offer"><p>${escapeHtml(screen.footer)}</p><div>${escapeHtml(screen.button)}</div></div>`
            : ""
        }
      </section>`;
  }

  return `<!doctype html>
  <html>
    <head>
      <meta charset="utf-8">
      <style>
        * { box-sizing: border-box; }
        body {
          margin: 0;
          width: ${width}px;
          height: ${height}px;
          background: #050505;
          color: white;
          font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
          overflow: hidden;
        }
        .shot {
          position: relative;
          width: 100%;
          height: 100%;
          background: linear-gradient(180deg, #07080d 0%, #10141c 48%, #030407 100%);
        }
        .caption {
          position: absolute;
          left: 72px;
          right: 72px;
          top: 72px;
          color: rgba(255,255,255,0.62);
          font-size: 34px;
          font-weight: 500;
          letter-spacing: 0;
        }
        .tap {
          position: absolute;
          inset: 0;
          display: grid;
          place-items: center;
        }
        .count {
          font-size: ${Math.round(width * 0.137)}px;
          font-weight: 650;
          letter-spacing: 0;
          text-shadow: 0 0 34px rgba(196,232,255,0.26), 0 18px 44px rgba(0,0,0,0.38);
        }
        .away {
          position: absolute;
          top: ${Math.round(height * 0.546)}px;
          left: 50%;
          transform: translateX(-50%);
          text-align: center;
          color: rgba(255,255,255,0.88);
          font-size: 30px;
          white-space: nowrap;
          padding: 20px 30px;
          border: 1px solid rgba(255,255,255,0.18);
          border-radius: 999px;
          background: linear-gradient(180deg, rgba(255,255,255,0.18), rgba(255,255,255,0.08));
          box-shadow: 0 24px 80px rgba(0,0,0,0.36), inset 0 1px 0 rgba(255,255,255,0.22);
        }
        .offer {
          position: absolute;
          left: 72px;
          right: 72px;
          bottom: ${Math.round(height * 0.054)}px;
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 26px;
          padding: 34px 30px;
          border: 1px solid rgba(255,255,255,0.18);
          border-radius: 56px;
          background: linear-gradient(180deg, rgba(255,255,255,0.18), rgba(255,255,255,0.08));
          box-shadow: 0 24px 80px rgba(0,0,0,0.36), inset 0 1px 0 rgba(255,255,255,0.22);
        }
        .offer p {
          margin: 0;
          color: rgba(255,255,255,0.78);
          font-size: 34px;
        }
        .offer div, .save {
          background: linear-gradient(180deg, rgba(255,255,255,0.98), rgba(221,235,255,0.94));
          color: #070a0f;
          font-size: 32px;
          font-weight: 650;
          padding: 24px 34px;
          border: 1px solid rgba(255,255,255,0.72);
          border-radius: 999px;
          box-shadow: 0 20px 54px rgba(200,236,255,0.18);
        }
        .leaderboard, .namePrompt {
          position: absolute;
          inset: 0;
          padding: 112px 64px 96px;
        }
        h1 {
          margin: 0 0 54px;
          font-size: 64px;
          font-weight: 650;
          letter-spacing: 0;
        }
        .rows {
          display: flex;
          flex-direction: column;
          gap: 28px;
        }
        .row {
          display: grid;
          grid-template-columns: 96px 1fr auto;
          gap: 24px;
          align-items: center;
          color: rgba(255,255,255,0.72);
          font-size: 34px;
          padding: 18px 20px;
        }
        .row.me {
          color: white;
          font-weight: 700;
          border: 1px solid rgba(255,255,255,0.18);
          border-radius: 30px;
          background: linear-gradient(180deg, rgba(255,255,255,0.18), rgba(255,255,255,0.08));
          box-shadow: 0 24px 80px rgba(0,0,0,0.30), inset 0 1px 0 rgba(255,255,255,0.20);
        }
        .you {
          position: absolute;
          left: 64px;
          right: 64px;
          bottom: 96px;
          margin: 0;
          color: rgba(255,255,255,0.82);
          text-align: center;
          font-size: 30px;
          padding: 24px 26px;
          border: 1px solid rgba(255,255,255,0.18);
          border-radius: 36px;
          background: linear-gradient(180deg, rgba(255,255,255,0.18), rgba(255,255,255,0.08));
          box-shadow: 0 24px 80px rgba(0,0,0,0.36), inset 0 1px 0 rgba(255,255,255,0.22);
        }
        label {
          display: block;
          margin-top: 18px;
          font-size: 34px;
        }
        .input {
          margin: 24px 0;
          width: 100%;
          background: rgba(255,255,255,0.10);
          color: rgba(255,255,255,0.38);
          padding: 30px 32px;
          font-size: 40px;
          border: 1px solid rgba(255,255,255,0.16);
          border-radius: 30px;
        }
        .save {
          display: inline-block;
        }
      </style>
    </head>
    <body>
      <main class="shot">
        <div class="caption">${escapeHtml(screen.caption)}</div>
        ${content}
      </main>
    </body>
  </html>`;
}

function featureHtml() {
  return `<!doctype html>
  <html>
    <head>
      <meta charset="utf-8">
      <style>
        body {
          margin: 0;
          width: 1024px;
          height: 500px;
          background: linear-gradient(180deg, #07080d 0%, #10141c 48%, #030407 100%);
          color: white;
          font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
          overflow: hidden;
        }
        main {
          position: relative;
          width: 100%;
          height: 100%;
          display: grid;
          place-items: center;
        }
        .count {
          font-size: 128px;
          font-weight: 700;
          letter-spacing: 0;
          text-shadow: 0 0 34px rgba(196,232,255,0.26), 0 18px 44px rgba(0,0,0,0.38);
        }
        .brand {
          position: absolute;
          left: 56px;
          bottom: 44px;
          font-size: 36px;
          font-weight: 650;
        }
        .line {
          position: absolute;
          right: 56px;
          bottom: 48px;
          color: rgba(255,255,255,0.62);
          font-size: 26px;
        }
      </style>
    </head>
    <body>
      <main>
        <div class="count">48,372</div>
        <div class="brand">Stack</div>
        <div class="line">Tap. Stack. Repeat.</div>
      </main>
    </body>
  </html>`;
}

function iconHtml() {
  return `<!doctype html>
  <html>
    <head>
      <meta charset="utf-8">
      <style>
        body {
          margin: 0;
          width: 512px;
          height: 512px;
          background: #000;
          display: grid;
          place-items: center;
        }
        .mark {
          display: flex;
          flex-direction: column;
          gap: 24px;
          align-items: center;
        }
        .bar {
          height: 36px;
          background: #fff;
          border-radius: 0;
        }
        .one { width: 160px; }
        .two { width: 192px; }
        .three { width: 144px; }
      </style>
    </head>
    <body>
      <div class="mark">
        <div class="bar one"></div>
        <div class="bar two"></div>
        <div class="bar three"></div>
      </div>
    </body>
  </html>`;
}

async function screenshot(page, html, width, height, file) {
  await page.setViewportSize({ width, height });
  await page.setContent(html, { waitUntil: "load" });
  await page.screenshot({ path: path.join(outDir, file), fullPage: false });
}

const browser = await chromium.launch({ channel: "chrome", headless: true });
const page = await browser.newPage({ deviceScaleFactor: 1 });

await screenshot(page, iconHtml(), 512, 512, "icon-512.png");
await screenshot(page, featureHtml(), 1024, 500, "feature-graphic-1024x500.png");
for (const screen of phoneScreens) {
  await screenshot(page, appScreenHtml(screen), 1080, 1920, screen.file);
}
for (const screen of tabletScreens) {
  await screenshot(page, appScreenHtml(screen, 1600, 2560), 1600, 2560, screen.file);
}

await browser.close();

console.log(`Generated ${phoneScreens.length + tabletScreens.length + 2} Play Store assets in ${path.relative(repoRoot, outDir)}`);
