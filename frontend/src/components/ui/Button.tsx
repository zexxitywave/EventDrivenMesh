import type { ButtonHTMLAttributes, ReactNode } from "react";
import clsx from "clsx";

interface ButtonProps
  extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode;
  loading?: boolean;
  fullWidth?: boolean;
  leftIcon?: ReactNode;
  rightIcon?: ReactNode;
  variant?: "primary" | "secondary" | "danger" | "ghost";
}

const Button = ({
  children,
  loading = false,
  fullWidth = false,
  leftIcon,
  rightIcon,
  variant = "primary",
  className,
  disabled,
  ...props
}: ButtonProps) => {
  const baseClasses =
    "inline-flex items-center justify-center gap-2 rounded-xl text-sm font-semibold transition-all duration-200 focus:outline-none focus:ring-4 disabled:cursor-not-allowed disabled:opacity-60";

  const sizeClasses = "h-11 px-5";

  const widthClasses = fullWidth ? "w-full" : "";

  const variants = {
    primary:
      "bg-[#FF9900] text-white border border-[#FF9900] hover:bg-[#F08804] hover:border-[#F08804] focus:ring-orange-200 active:scale-[0.98]",

    secondary:
      "bg-white text-gray-800 border border-gray-300 hover:bg-gray-100 focus:ring-gray-200 active:scale-[0.98]",

    danger:
      "bg-red-600 text-white border border-red-600 hover:bg-red-700 hover:border-red-700 focus:ring-red-200 active:scale-[0.98]",

    ghost:
      "bg-transparent text-gray-700 hover:bg-gray-100 border border-transparent focus:ring-gray-200",
  };

  return (
    <button
      disabled={disabled || loading}
      className={clsx(
        baseClasses,
        sizeClasses,
        widthClasses,
        variants[variant],
        className
      )}
      {...props}
    >
      {loading ? (
        <>
          <svg
            className="h-5 w-5 animate-spin"
            viewBox="0 0 24 24"
            fill="none"
          >
            <circle
              cx="12"
              cy="12"
              r="10"
              stroke="currentColor"
              strokeOpacity="0.25"
              strokeWidth="4"
            />

            <path
              d="M22 12a10 10 0 00-10-10"
              stroke="currentColor"
              strokeWidth="4"
              strokeLinecap="round"
            />
          </svg>

          <span>Please wait...</span>
        </>
      ) : (
        <>
          {leftIcon}

          <span>{children}</span>

          {rightIcon}
        </>
      )}
    </button>
  );
};

export default Button;