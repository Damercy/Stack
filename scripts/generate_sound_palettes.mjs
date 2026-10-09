import fs from "node:fs";
import path from "node:path";

const sampleRate = 44_100;
const outputDir = path.resolve("app/src/main/res/raw");
fs.mkdirSync(outputDir, { recursive: true });

const palettes = [
  ["stack_crystal", 0.58, (t, f) =>
    Math.sin(2 * Math.PI * f * t) * Math.exp(-7 * t) +
    0.42 * Math.sin(2 * Math.PI * f * 2.01 * t) * Math.exp(-10 * t) +
    0.18 * Math.sin(2 * Math.PI * f * 3.96 * t) * Math.exp(-14 * t)],
  ["stack_felt", 0.48, (t, f) =>
    Math.sin(2 * Math.PI * f * t) * Math.exp(-5.4 * t) +
    0.28 * Math.sin(2 * Math.PI * f * 2 * t) * Math.exp(-8 * t) +
    0.08 * Math.sin(2 * Math.PI * f * 3 * t) * Math.exp(-15 * t)],
  ["stack_celeste", 0.62, (t, f) =>
    Math.sin(2 * Math.PI * f * 2 * t) * Math.exp(-7 * t) +
    0.55 * Math.sin(2 * Math.PI * f * 3.02 * t) * Math.exp(-10 * t) +
    0.22 * Math.sin(2 * Math.PI * f * 5.1 * t) * Math.exp(-15 * t)],
  ["stack_marimba", 0.34, (t, f) =>
    Math.sin(2 * Math.PI * f * t) * Math.exp(-11 * t) +
    0.26 * Math.sin(2 * Math.PI * f * 3.9 * t) * Math.exp(-18 * t)],
  ["stack_kalimba", 0.42, (t, f) =>
    Math.sin(2 * Math.PI * f * t) * Math.exp(-8 * t) +
    0.48 * Math.sin(2 * Math.PI * f * 2.77 * t) * Math.exp(-13 * t) +
    0.15 * Math.sin(2 * Math.PI * f * 5.2 * t) * Math.exp(-18 * t)],
  ["stack_pizzicato", 0.28, (t, f) => {
    const saw = 2 * ((f * t) % 1) - 1;
    return (0.62 * saw + 0.38 * Math.sin(2 * Math.PI * f * t)) * Math.exp(-14 * t);
  }],
  ["stack_temple", 0.82, (t, f) =>
    Math.sin(2 * Math.PI * f * 0.5 * t) * Math.exp(-3.8 * t) +
    0.38 * Math.sin(2 * Math.PI * f * 1.51 * t) * Math.exp(-5.2 * t) +
    0.2 * Math.sin(2 * Math.PI * f * 2.42 * t) * Math.exp(-7 * t)],
  ["stack_bloom", 0.55, (t, f) => {
    const attack = Math.min(1, t / 0.025);
    return attack * Math.exp(-4.8 * t) * (
      0.75 * Math.sin(2 * Math.PI * f * t) +
      0.25 * Math.sin(2 * Math.PI * f * 1.005 * t)
    );
  }],
  ["stack_fifth", 0.45, (t, f) =>
    Math.sin(2 * Math.PI * f * t) * Math.exp(-6.2 * t) +
    0.34 * Math.sin(2 * Math.PI * f * 2 * t) * Math.exp(-8.5 * t) +
    0.12 * Math.sin(2 * Math.PI * f * 4 * t) * Math.exp(-12 * t)],
  ["stack_joy", 0.5, (t, f) =>
    Math.sin(2 * Math.PI * f * t) * Math.exp(-5.5 * t) +
    0.25 * Math.sin(2 * Math.PI * f * 2 * t) * Math.exp(-7.5 * t) +
    0.1 * Math.sin(2 * Math.PI * f * 3 * t) * Math.exp(-10 * t)],
];

function writeWav(name, duration, synth) {
  const count = Math.floor(sampleRate * duration);
  const data = Buffer.alloc(count * 2);
  let previous = 0;
  for (let i = 0; i < count; i += 1) {
    const t = i / sampleRate;
    const attack = Math.min(1, t / 0.004);
    const raw = synth(t, 261.63) * attack;
    previous = previous * 0.12 + raw * 0.88;
    const sample = Math.max(-1, Math.min(1, previous * 0.56));
    data.writeInt16LE(Math.round(sample * 32767), i * 2);
  }

  const header = Buffer.alloc(44);
  header.write("RIFF", 0);
  header.writeUInt32LE(36 + data.length, 4);
  header.write("WAVE", 8);
  header.write("fmt ", 12);
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20);
  header.writeUInt16LE(1, 22);
  header.writeUInt32LE(sampleRate, 24);
  header.writeUInt32LE(sampleRate * 2, 28);
  header.writeUInt16LE(2, 32);
  header.writeUInt16LE(16, 34);
  header.write("data", 36);
  header.writeUInt32LE(data.length, 40);
  fs.writeFileSync(path.join(outputDir, `${name}.wav`), Buffer.concat([header, data]));
}

for (const [name, duration, synth] of palettes) writeWav(name, duration, synth);
console.log(`Generated ${palettes.length} Stack sound palettes.`);
