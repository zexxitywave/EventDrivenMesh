import type { AuthResponse } from "../types/auth";

const AUTH_KEY = "auth";

export const saveAuth = (auth: AuthResponse): void => {
  localStorage.setItem(AUTH_KEY, JSON.stringify(auth));
};

export const getAuth = (): AuthResponse | null => {
  const auth = localStorage.getItem(AUTH_KEY);

  if (!auth) {
    return null;
  }

  return JSON.parse(auth);
};

export const clearAuth = (): void => {
  localStorage.removeItem(AUTH_KEY);
};

export const logoutUser = (): void => {
  clearAuth();
};