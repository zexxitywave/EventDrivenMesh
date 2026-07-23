import {
  forwardRef,
  type InputHTMLAttributes,
} from "react";
import clsx from "clsx";

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
}

const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, className, id, ...props }, ref) => {
    return (
      <div className="w-full">
        <label
          htmlFor={id}
          className="mb-2 block text-sm font-semibold text-gray-700"
        >
          {label}
        </label>

        <input
          ref={ref}
          id={id}
          className={clsx(
            "h-12 w-full rounded-xl border bg-white px-4 text-sm text-gray-900 outline-none transition-all duration-200 placeholder:text-gray-400",
            error
              ? "border-red-500 ring-2 ring-red-100"
              : "border-gray-300 focus:border-[#FF9900] focus:ring-4 focus:ring-orange-100",
            className
          )}
          {...props}
        />

        {error && (
          <p className="mt-2 text-sm text-red-600">
            {error}
          </p>
        )}
      </div>
    );
  }
);

Input.displayName = "Input";

export default Input;