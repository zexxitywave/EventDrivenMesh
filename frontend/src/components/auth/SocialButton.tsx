import type { ReactNode } from "react";

interface SocialButtonProps {
  icon: ReactNode;
  text: string;
  onClick?: () => void;
}

const SocialButton = ({
  icon,
  text,
  onClick,
}: SocialButtonProps) => {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex w-full items-center justify-center gap-3 rounded-xl border border-slate-300 bg-white px-4 py-3 font-medium text-slate-700 transition-all duration-200 hover:bg-slate-50 hover:border-slate-400"
    >
      {icon}

      <span>{text}</span>
    </button>
  );
};

export default SocialButton;