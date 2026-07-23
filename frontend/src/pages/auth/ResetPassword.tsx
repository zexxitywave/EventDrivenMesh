import { useState } from "react";
import { useForm } from "react-hook-form";
import { useLocation, useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import axios from "axios";

import type { ResetPasswordRequest } from "../../types/auth";

import { resetPassword } from "../../services/auth/authService";

import AuthLayout from "../../layouts/AuthLayout";

import Button from "../../components/ui/Button";
import Card from "../../components/ui/Card";
import Input from "../../components/ui/Input";
import PasswordInput from "../../components/auth/PasswordInput";

type ResetPasswordForm = ResetPasswordRequest & {
  confirmPassword: string;
};

const ResetPassword = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const email = location.state?.email ?? "";

  const [loading, setLoading] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<ResetPasswordForm>({
    defaultValues: {
      email,
      otp: "",
      newPassword: "",
      confirmPassword: "",
    },
  });

  const password = watch("newPassword");

  const onSubmit = async (data: ResetPasswordForm) => {
    try {
      setLoading(true);

      const { confirmPassword, ...request } = data;

      await resetPassword(request);

      toast.success("Password reset successfully!");

      navigate("/login");
    } catch (error: unknown) {
      if (axios.isAxiosError(error)) {
        toast.error(
          error.response?.data?.message ??
            "Unable to reset password."
        );
      } else {
        toast.error("Something went wrong.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthLayout>
      <Card className="max-w-md">
        <h1 className="text-3xl font-bold text-gray-900">
          Reset Password
        </h1>

        <p className="mt-2 text-sm text-gray-500">
          Enter the OTP sent to your email and choose a new password.
        </p>

        <form
          onSubmit={handleSubmit(onSubmit)}
          className="mt-8 space-y-5"
        >
          <Input
            label="Email Address"
            type="email"
            readOnly
            {...register("email")}
          />

          <Input
            label="OTP"
            placeholder="Enter OTP"
            error={errors.otp?.message}
            {...register("otp", {
              required: "OTP is required",
            })}
          />

          <PasswordInput
            label="New Password"
            placeholder="Enter new password"
            error={errors.newPassword?.message}
            {...register("newPassword", {
              required: "New password is required",
              minLength: {
                value: 6,
                message: "Password must be at least 6 characters",
              },
            })}
          />

          <PasswordInput
            label="Confirm Password"
            placeholder="Confirm new password"
            error={errors.confirmPassword?.message}
            {...register("confirmPassword", {
              required: "Please confirm your password",
              validate: (value) =>
                value === password || "Passwords do not match",
            })}
          />

          <Button
            type="submit"
            fullWidth
            loading={loading}
          >
            Reset Password
          </Button>

          <Button
            type="button"
            variant="secondary"
            fullWidth
            onClick={() => navigate("/login")}
          >
            Back to Login
          </Button>
        </form>
      </Card>
    </AuthLayout>
  );
};

export default ResetPassword;