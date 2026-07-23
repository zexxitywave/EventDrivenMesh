import {
  forwardRef,
  useState,
} from "react";

import { Eye, EyeOff } from "lucide-react";

import Input from "../ui/Input";

interface PasswordInputProps
  extends React.InputHTMLAttributes<HTMLInputElement> {
  label: string;
  error?: string;
}

const PasswordInput = forwardRef<
  HTMLInputElement,
  PasswordInputProps
>(({ label, error, ...props }, ref) => {
  const [show, setShow] = useState(false);

  return (
    <div className="relative">

      <Input
        ref={ref}
        label={label}
        type={show ? "text" : "password"}
        error={error}
        {...props}
      />

      <button
        type="button"
        onClick={() => setShow(!show)}
        className="absolute right-3 top-[38px] text-gray-500 hover:text-black"
      >
        {show ? (
          <EyeOff size={18} />
        ) : (
          <Eye size={18} />
        )}
      </button>

    </div>
  );
});

PasswordInput.displayName = "PasswordInput";

export default PasswordInput;