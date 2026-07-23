import { Link, useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { useState } from "react";
import toast from "react-hot-toast";
import axios from "axios";

import type { LoginRequest } from "../../types/auth";

import { login as loginUser } from "../../services/auth/authService";
import { useAuth } from "../../context/AuthContext";

import AuthLayout from "../../layouts/AuthLayout";

import PasswordInput from "../../components/auth/PasswordInput";

import Button from "../../components/ui/Button";
import Card from "../../components/ui/Card";
import Input from "../../components/ui/Input";
import Divider from "../../components/ui/Divider";

const Login = () => {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [loading, setLoading] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginRequest>({
    defaultValues: {
      email: "",
      password: "",
    },
  });

  const onSubmit = async (data: LoginRequest) => {
    try {
      setLoading(true);

      const response = await loginUser(data);

      login(response);

      toast.success("Login successful!");

      navigate("/");
    } catch (error: unknown) {
      if (axios.isAxiosError(error)) {
        toast.error(
          error.response?.data?.message ??
            "Invalid email or password."
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
          Welcome Back
        </h1>

        <p className="mt-2 text-sm text-gray-500">
          Sign in to your ShopSphere account.
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
            autoComplete="current-password"
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

          <div className="flex justify-end">

            <Link
              to="/forgot-password"
              className="text-sm text-[#0066c0] hover:underline"
            >
              Forgot Password?
            </Link>

          </div>

          <Button
            type="submit"
            fullWidth
            loading={loading}
          >
            Sign In
          </Button>

          <Divider text="New to ShopSphere?" />

          <Button
            type="button"
            variant="secondary"
            fullWidth
            onClick={() => navigate("/register")}
          >
            Create Account
          </Button>

        </form>
      </Card>
    </AuthLayout>
  );
};

export default Login;