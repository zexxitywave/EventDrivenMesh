import clsx from "clsx";

interface DividerProps {
  text?: string;
  className?: string;
}

const Divider = ({
  text = "OR",
  className,
}: DividerProps) => {
  return (
    <div
      className={clsx(
        "flex items-center gap-4 py-2",
        className
      )}
    >
      <div className="h-px flex-1 bg-gray-300" />

      <span className="text-xs font-semibold uppercase tracking-wider text-gray-500">
        {text}
      </span>

      <div className="h-px flex-1 bg-gray-300" />
    </div>
  );
};

export default Divider;