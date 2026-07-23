import { Link, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { useState } from "react";
import toast from "react-hot-toast";
import axios from "axios";

import type { RegisterRequest } from "../../types/auth";

import { register as registerUser } from "../../services/auth/authService";

import AuthLayout from "../../layouts/AuthLayout";

import PasswordInput from "../../components/auth/PasswordInput";

import Button from "../../components/ui/Button";
import Card from "../../components/ui/Card";
import Divider from "../../components/ui/Divider";
import Input from "../../components/ui/Input";

type RegisterForm = RegisterRequest & {
  confirmPassword: string;
};

const Register = () => {
  const navigate = useNavigate();

  const [loading, setLoading] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<RegisterForm>({
    defaultValues: {
      name: "",
      email: "",
      password: "",
      confirmPassword: "",
    },
  });

  const password = watch("password");

  const onSubmit = async (data: RegisterForm) => {
    try {
      setLoading(true);

      const { confirmPassword, ...request } = data;

      await registerUser(request);

      toast.success(
        "Registration successful! Please verify your email."
      );

      navigate("/verify-email", {
        state: {
          email: request.email,
        },
      });
    } catch (error: unknown) {
      if (axios.isAxiosError(error)) {
        toast.error(
          error.response?.data?.message ??
            "Registration failed."
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
          Create Account
        </h1>

        <p className="mt-2 text-sm text-gray-500">
          Register to start using ShopSphere.
        </p>

        <form
          onSubmit={handleSubmit(onSubmit)}
          className="mt-8 space-y-5"
        >

          <Input
            id="name"
            label="Full Name"
            placeholder="Enter your name"
            error={errors.name?.message}
            {...register("name", {
              required: "Name is required",
              minLength: {
                value: 3,
                message: "Minimum 3 characters",
              },
            })}
          />

          <Input
            id="email"
            label="Email Address"
            type="email"
            placeholder="Enter your email"
            error={errors.email?.message}
            {...register("email", {
              required: "Email is required",
              pattern: {
                value:
                  /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
                message: "Invalid email address",
              },
            })}
          />

          <PasswordInput
            id="password"
            label="Password"
            placeholder="Enter your password"
            error={errors.password?.message}
            {...register("password", {
              required: "Password is required",
              minLength: {
                value: 6,
                message:
                  "Password must be at least 6 characters",
              },
            })}
          />

          <PasswordInput
            id="confirmPassword"
            label="Confirm Password"
            placeholder="Confirm your password"
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
            Create Account
          </Button>

          <Divider text="Already have an account?" />

          <Button
            type="button"
            variant="secondary"
            fullWidth
            onClick={() => navigate("/login")}
          >
            Sign In
          </Button>

          <p className="text-center text-sm text-gray-600">
            Already registered?{" "}
            <Link
              to="/login"
              className="text-[#0066c0] hover:underline"
            >
              Login here
            </Link>
          </p>

        </form>
      </Card>
    </AuthLayout>
  );
};

export default Register;