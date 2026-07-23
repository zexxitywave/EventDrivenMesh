import type { ReactNode } from "react";
import clsx from "clsx";

interface CardProps {
  children: ReactNode;
  className?: string;
}

const Card = ({ children, className }: CardProps) => {
  return (
    <div
      className={clsx(
      "w-full rounded-2xl bg-white border border-gray-200 shadow-lg",
      "p-6",
      className
    )}
    >
      {children}
    </div>
  );
};

export default Card;