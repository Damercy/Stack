import { initializeApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";

initializeApp();

const db = getFirestore();

export const resetYesterdayLeaderboard = onSchedule(
  {
    schedule: "5 0 * * *",
    timeZone: "Asia/Kolkata",
  },
  async () => {
    const yesterday = new Date();
    yesterday.setUTCDate(yesterday.getUTCDate() - 1);
    const dayKey = new Intl.DateTimeFormat("en-CA", {
      timeZone: "Asia/Kolkata",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).format(yesterday);

    await db.collection("daily_leaderboards").doc(dayKey).set(
      {
        closed_at: Timestamp.now(),
        status: "closed",
      },
      { merge: true },
    );
  },
);

export { stylePackStatus, verifyStylePack } from "./stylePack.js";
