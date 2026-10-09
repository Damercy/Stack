import { getRemoteConfig } from "firebase-admin/remote-config";
import { getFirestore, FieldValue } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { GoogleAuth } from "google-auth-library";

const packageName = "com.stackapp.stack";
const productId = "off_balance_style_pack";
const auth = new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/androidpublisher"] });
const options = { enforceAppCheck: true, timeoutSeconds: 30, maxInstances: 2 };

type Receipt = { purchaseState?: number; acknowledgementState?: number; productId?: string; consumptionState?: number };
export function validReceipt(receipt: Receipt): boolean {
  return receipt.purchaseState === 0 && receipt.consumptionState === 0 &&
    (receipt.productId === undefined || receipt.productId === productId);
}
async function publisher<T>(path: string, method: "GET" | "POST" = "GET"): Promise<T> {
  const client = await auth.getClient();
  const result = await client.request<T>({
    url: `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${packageName}/${path}`,
    method, ...(method === "POST" ? { data: {} } : {}), timeout: 8000,
  });
  return result.data;
}
async function verify(token: string): Promise<boolean> {
  const path = `purchases/products/${productId}/tokens/${encodeURIComponent(token)}`;
  const receipt = await publisher<Receipt>(path);
  if (!validReceipt(receipt)) return false;
  if (receipt.acknowledgementState === 0) await publisher(`${path}:acknowledge`, "POST");
  return true;
}

/** Readiness requires both launch controls and working Play API credentials. */
export const stylePackStatus = onCall(options, async request => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in to restore purchases.");
  try {
    const template = await getRemoteConfig().getTemplate();
    const enabled = (key: string) => {
      const value = template.parameters[key]?.defaultValue;
      return value && "value" in value && value.value === "true";
    };
    let ready = false;
    if (enabled("payments_enabled") && enabled("payment_verification_ready")) {
      await publisher(`monetization/onetimeproducts/${productId}`);
      ready = true;
    }
    const ref = getFirestore().collection("style_entitlements").doc(request.auth.uid);
    const previous = (await ref.get()).data();
    const owned = typeof previous?.token === "string" && await verify(previous.token);
    if (previous && !owned) await ref.delete();
    return { ready, owned, productId };
  } catch {
    throw new HttpsError("unavailable", "Store unavailable. Try again later.");
  }
});

/** A flag, pending payment or client assertion can never grant the pack. */
export const verifyStylePack = onCall(options, async request => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Sign in to restore purchases.");
  const token = request.data?.token;
  if (request.data?.productId !== productId || typeof token !== "string" || token.length < 10 || token.length > 4096) {
    throw new HttpsError("invalid-argument", "Invalid purchase.");
  }
  try {
    const owned = await verify(token);
    const ref = getFirestore().collection("style_entitlements").doc(request.auth.uid);
    if (owned) await ref.set({ token, productId, verifiedAt: FieldValue.serverTimestamp() });
    else await ref.delete();
    return { owned, productId };
  } catch {
    throw new HttpsError("unavailable", "Verification unavailable. Restore purchases to retry.");
  }
});
