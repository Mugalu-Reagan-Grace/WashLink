import { auth } from "./firebase";

const API_BASE_URL = (import.meta.env.VITE_WASHLINK_API_BASE_URL || "").replace(/\/+$/, "");

export async function washLinkApiPost(path, data = {}) {
  if (!API_BASE_URL) throw new Error("The WashLink API URL is not configured.");
  const user = auth?.currentUser;
  if (!user) throw new Error("Sign in again before using the WashLink service.");
  const token = await user.getIdToken();
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(data),
  });
  const result = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(result.message || "The WashLink service could not complete the request.");
  }
  return result;
}
