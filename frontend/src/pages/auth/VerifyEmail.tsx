import { useState } from "react";
import { useForm } from "react-hook-form";
import { useLocation, useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import axios from "axios";

import type { VerifyEmailRequest } from "../../types/auth";

import {
  verifyEmail,
  resendVerification,
} from "../../services/auth/authService";

import AuthLayout from "../../layouts/AuthLayout";

import Button from "../../components/ui/Button";
import Card from "../../components/ui/Card";
import Input from "../../components/ui/Input";

const VerifyEmail = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const email = location.state?.email ?? "";

  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<VerifyEmailRequest>({
    defaultValues: {
      email,
      otp: "",
    },
  });

  const onSubmit = async (data: VerifyEmailRequest) => {
    try {
      setLoading(true);

      await verifyEmail(data);

      toast.success("Email verified successfully!");

      navigate("/login");
    } catch (error: unknown) {
      if (axios.isAxiosError(error)) {
        toast.error(
          error.response?.data?.message ??
            "Verification failed."
        );
      } else {
        toast.error("Something went wrong.");
      }
    } finally {
      setLoading(false);
    }
  };

  const handleResend = async () => {
    if (!email) {
      toast.error("Email not found.");
      return;
    }

    try {
      setResending(true);

      await resendVerification(email);

      toast.success("Verification code sent.");
    } catch (error: unknown) {
      if (axios.isAxiosError(error)) {
        toast.error(
          error.response?.data?.message ??
            "Unable to resend OTP."
        );
      } else {
        toast.error("Something went wrong.");
      }
    } finally {
      setResending(false);
    }
  };

  return (
    <AuthLayout>
      <Card className="max-w-md">

        <h1 className="text-3xl font-bold text-gray-900">
          Verify Email
        </h1>

        <p className="mt-2 text-sm text-gray-500">
          Enter the verification code sent to your email.
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
            label="Verification Code"
            placeholder="Enter OTP"
            error={errors.otp?.message}
            {...register("otp", {
              required: "Verification code is required",
            })}
          />

          <Button
            type="submit"
            fullWidth
            loading={loading}
          >
            Verify Email
          </Button>

          <Button
            type="button"
            variant="secondary"
            fullWidth
            loading={resending}
            onClick={handleResend}
          >
            Resend Code
          </Button>

        </form>

      </Card>
    </AuthLayout>
  );
};

export default VerifyEmail;