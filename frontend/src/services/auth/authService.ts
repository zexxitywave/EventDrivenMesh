import api from "../api";

import type {
  RegisterRequest,
  LoginRequest,
  VerifyEmailRequest,
  ForgotPasswordRequest,
  ResetPasswordRequest,
  AuthResponse,
} from "../../types/auth"

export const register = async (data: RegisterRequest) => {
  const response = await api.post(
    "/api/auth/register",
    data
  );

  return response.data;
};

export const verifyEmail = async (
  data: VerifyEmailRequest
) => {
  const response = await api.post(
    "/api/auth/verify-email",
    data
  );

  return response.data;
};

export const login = async (
  data: LoginRequest
): Promise<AuthResponse> => {
  const response = await api.post<AuthResponse>(
    "/api/auth/login",
    data
  );

  return response.data;
};

export const forgotPassword = async (
  data: ForgotPasswordRequest
) => {
  const response = await api.post(
    "/api/auth/forgot-password",
    data
  );

  return response.data;
};

export const resetPassword = async (
  data: ResetPasswordRequest
) => {
  const response = await api.post(
    "/api/auth/reset-password",
    data
  );

  return response.data;
};

export const resendVerification = async (
  email: string
) => {
  const response = await api.post(
    `/api/auth/resend-verification?email=${encodeURIComponent(email)}`
  );

  return response.data;
};