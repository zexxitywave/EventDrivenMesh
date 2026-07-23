import { useState } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import axios from "axios";

import type { ForgotPasswordRequest } from "../../types/auth";

import { forgotPassword } from "../../services/auth/authService";

import AuthLayout from "../../layouts/AuthLayout";

import Button from "../../components/ui/Button";
import Card from "../../components/ui/Card";
import Input from "../../components/ui/Input";

const ForgotPassword = () => {
  const navigate = useNavigate();

  const [loading, setLoading] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ForgotPasswordRequest>({
    defaultValues: {
      email: "",
    },
  });

  const onSubmit = async (data: ForgotPasswordRequest) => {
    try {
      setLoading(true);

      await forgotPassword(data);

      toast.success(
        "Password reset OTP has been sent to your email."
      );

      navigate("/reset-password", {
        state: {
          email: data.email,
        },
      });
    } catch (error: unknown) {
      if (axios.isAxiosError(error)) {
        toast.error(
          error.response?.data?.message ??
            "Unable to send reset OTP."
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
          Forgot Password
        </h1>

        <p className="mt-2 text-sm text-gray-500">
          Enter your registered email address and we'll send you a password reset code.
        </p>

        <form
          onSubmit={handleSubmit(onSubmit)}
          className="mt-8 space-y-5"
        >

          <Input
            id="email"
            label="Email Address"
            type="email"
            placeholder="Enter your email"
            autoComplete="email"
            error={errors.email?.message}
            {...register("email", {
              required: "Email is required",
              pattern: {
                value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                message: "Please enter a valid email address",
              },
            })}
          />

          <Button
            type="submit"
            fullWidth
            loading={loading}
          >
            Send Reset Code
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

export default ForgotPassword;